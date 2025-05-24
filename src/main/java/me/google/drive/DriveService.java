package me.google.drive;

import com.google.api.client.http.ByteArrayContent;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;

import me.merunko.PenguinGuard.Entry.Entry;
import me.merunko.PenguinGuard.security.Converter;
import me.merunko.PenguinGuard.security.Encryption;
import me.merunko.utilities.EnvironmentVariablesLoader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Optional;

public class DriveService {

    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 1000;
    private static final long MAX_FILE_SIZE_BYTES = 1024 * 1024; // 1MB
    private static final String FILE_MAGIC = EnvironmentVariablesLoader.getFileMagic();

    private final GoogleCredentials credentials;
    private final Converter converter;
    private final String applicationName;
    private final String folderMimeType;
    private final String fileName;

    public DriveService(GoogleCredentials credentials, Converter converter,
                        String applicationName, String folderMimeType, String fileName) {
        this.credentials = credentials;
        this.converter = converter;
        this.applicationName = applicationName;
        this.folderMimeType = folderMimeType;
        this.fileName = fileName;
    }

    public DriveService(GoogleCredentials credentials, Converter converter) {
        this(credentials, converter,
                EnvironmentVariablesLoader.getApplicationName(),
                EnvironmentVariablesLoader.getFolderMimeType(),
                EnvironmentVariablesLoader.getFileName());
    }

    private Drive createDriveService(String scope) {
        GoogleCredentials scopedCredentials = credentials.createScoped(Collections.singleton(scope));
        HttpRequestInitializer requestInitializer = new HttpCredentialsAdapter(scopedCredentials);

        return new Drive.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance(), requestInitializer)
                .setApplicationName(applicationName)
                .build();
    }

    private Drive getReadDriveService() {
        return createDriveService("https://www.googleapis.com/auth/drive.readonly");
    }

    private Drive getWriteDriveService() {
        return createDriveService("https://www.googleapis.com/auth/drive.file");
    }

    public boolean doesGuardExist() throws DriveOperationException {
        try {
            Drive driveService = getReadDriveService();
            Optional<String> folderId = findPenguinGuardFolderId(driveService);
            if (folderId.isEmpty()) {
                return false;
            }
            return findPasswordFileId(driveService, folderId.get()).isPresent();
        } catch (IOException e) {
            throw new DriveOperationException("Failed to check guard existence", e);
        }
    }

    public boolean createGuardFolderAndFile(String password) throws DriveOperationException {
        try {
            Drive driveService = getWriteDriveService();

            // Create the folder with retry
            String folderId = executeWithRetry(() -> {
                File folderMetadata = new File();
                folderMetadata.setName(applicationName);
                folderMetadata.setMimeType(folderMimeType);
                File folder = driveService.files().create(folderMetadata)
                        .setFields("id")
                        .execute();
                return folder.getId();
            });

            // Create the password file with retry with entries marker
            return executeWithRetry(() -> {
                // Convert password and add entries marker
                byte[] passwordContent = converter.convertToPGuard(password);
                String contentStr = new String(passwordContent, StandardCharsets.UTF_8)
                        + System.lineSeparator() + "===ENTRIES===";
                byte[] fileContent = contentStr.getBytes(StandardCharsets.UTF_8);

                File fileMetadata = new File();
                fileMetadata.setName(fileName);
                fileMetadata.setParents(Collections.singletonList(folderId));

                ByteArrayContent content = new ByteArrayContent(
                        "application/octet-stream",
                        fileContent
                );

                File file = driveService.files().create(fileMetadata, content)
                        .setFields("id")
                        .execute();

                return file != null && file.getId() != null;
            });
        } catch (Exception e) {
            throw new DriveOperationException("Failed to create guard folder and file", e);
        }
    }

    private Optional<String> findPenguinGuardFolderId(Drive driveService) throws IOException {
        String folderQuery = String.format(
                "name='%s' and mimeType='%s' and trashed=false",
                applicationName, folderMimeType
        );

        FileList folderResult = driveService.files().list()
                .setQ(folderQuery)
                .setSpaces("drive")
                .setFields("files(id)")
                .execute();

        return folderResult.getFiles().isEmpty() ?
                Optional.empty() :
                Optional.of(folderResult.getFiles().get(0).getId());
    }

    private Optional<String> findPasswordFileId(Drive driveService, String folderId) throws IOException {
        String fileQuery = String.format(
                "name='%s' and '%s' in parents and trashed=false",
                fileName, folderId
        );

        FileList fileResult = driveService.files().list()
                .setQ(fileQuery)
                .setSpaces("drive")
                .setFields("files(id, size)")
                .execute();

        return fileResult.getFiles().isEmpty() ?
                Optional.empty() :
                Optional.of(fileResult.getFiles().get(0).getId());
    }

    public InputStream downloadFileFromDrive() throws IOException, DriveOperationException {
        Drive driveService = getReadDriveService();

        String folderId = findPenguinGuardFolderId(driveService)
                .orElseThrow(() -> new DriveOperationException("PenguinGuard folder not found"));

        String fileId = findPasswordFileId(driveService, folderId)
                .orElseThrow(() -> new DriveOperationException(fileName + " file not found"));

        return executeWithRetry(() -> {
            File file = driveService.files().get(fileId).execute();
            if (file.getSize() != null && file.getSize() > MAX_FILE_SIZE_BYTES) {
                throw new DriveOperationException("File size exceeds maximum allowed limit");
            }
            return driveService.files().get(fileId).executeMediaAsInputStream();
        });
    }

    public void saveEntryToDrive(byte[] entry) throws DriveOperationException {
        try {
            Drive driveService = getWriteDriveService();

            String folderId = findPenguinGuardFolderId(driveService)
                    .orElseThrow(() -> new DriveOperationException(applicationName + " folder not found"));

            String fileId = findPasswordFileId(driveService, folderId)
                    .orElseThrow(() -> new DriveOperationException(fileName + " file not found"));

            // Get current content
            byte[] currentContent;
            try (InputStream is = driveService.files().get(fileId).executeMediaAsInputStream()) {
                currentContent = is.readAllBytes();
            }

            // Convert to string and ensure proper format
            String contentStr = new String(currentContent, StandardCharsets.UTF_8);

            // Find entries section
            int entriesIndex = contentStr.indexOf("===ENTRIES===");
            if (entriesIndex == -1) {
                // If missing, add it (shouldn't happen with new createGuardFolderAndFile)
                contentStr += System.lineSeparator() + "===ENTRIES===";
            }

            // Append new entry (single line)
            String entryStr = new String(entry, StandardCharsets.UTF_8).trim();
            String newContent = contentStr + (contentStr.endsWith("\n") ? "" : System.lineSeparator()) + entryStr;

            // Upload updated content
            ByteArrayContent content = new ByteArrayContent(
                    "application/octet-stream",
                    newContent.getBytes(StandardCharsets.UTF_8)
            );

            driveService.files().update(fileId, null, content).execute();

        } catch (Exception e) {
            throw new DriveOperationException("Failed to save entry to Drive", e);
        }
    }

    public void removeEntryFromDrive(Entry entry) throws DriveOperationException {
        try {
            this.credentials.refreshIfExpired();
            Drive driveService = getWriteDriveService();

            String folderId = findPenguinGuardFolderId(driveService)
                    .orElseThrow(() -> new DriveOperationException(applicationName + " folder not found"));

            String fileId = findPasswordFileId(driveService, folderId)
                    .orElseThrow(() -> new DriveOperationException(fileName + " file not found"));

            // 1. Get the complete file content
            byte[] fileContent = executeWithRetry(() -> {
                try (InputStream inputStream = driveService.files().get(fileId).executeMediaAsInputStream()) {
                    return inputStream.readAllBytes();
                }
            });

            // 2. Convert to String for processing
            String currentContent = new String(fileContent, StandardCharsets.UTF_8);

            // 3. Handle case where entries separator is missing
            if (!currentContent.contains("===ENTRIES===")) {
                // If file is empty or contains only the header
                if (currentContent.trim().isEmpty() ||
                        currentContent.length() <= FILE_MAGIC.length() + 4 + 4) { // magic + version + pass length
                    throw new DriveOperationException("No entries found in file");
                }

                // If we have content but no separator, treat everything after header as entries
                currentContent = currentContent + System.lineSeparator() + "===ENTRIES===";
            }

            // 4. Remove the entry from content
            String newContent = removeEntryFromContent(currentContent, entry);

            // 5. Convert back to bytes for storage
            byte[] newContentBytes = newContent.getBytes(StandardCharsets.UTF_8);

            // 6. Prepare for upload
            ByteArrayContent content = new ByteArrayContent(
                    "application/octet-stream",
                    newContentBytes
            );

            // 7. Update the file
            executeWithRetry(() -> {
                driveService.files().update(fileId, null, content).execute();
                return null;
            });

        } catch (DriveOperationException e) {
            throw e; // Re-throw our custom exceptions
        } catch (Exception e) {
            throw new DriveOperationException("Failed to remove entry from Drive: " + e.getMessage(), e);
        }
    }

    private String removeEntryFromContent(String content, Entry entry) throws DriveOperationException {
        try {
            // Split into header and entries
            int entriesIndex = content.indexOf("===ENTRIES===");
            if (entriesIndex == -1) {
                // This shouldn't happen since we handle it above, but just in case
                entriesIndex = content.length();
                content = content + System.lineSeparator() + "===ENTRIES===";
            }

            String header = content.substring(0, entriesIndex + "===ENTRIES===".length());
            String entriesContent = content.substring(entriesIndex + "===ENTRIES===".length());

            // Process entries
            String[] lines = entriesContent.split("\\R"); // Handles all line endings
            StringBuilder newContent = new StringBuilder(header);
            boolean entryRemoved = false;

            for (String line : lines) {
                if (line.trim().isEmpty()) continue;

                String[] entryParts = line.split("\\|\\|\\|");
                if (entryParts.length > 0 && !entryParts[0].equals(entry.id())) {
                    newContent.append(System.lineSeparator()).append(line);
                } else {
                    entryRemoved = true;
                }
            }

            if (!entryRemoved) {
                throw new DriveOperationException("Entry not found in file");
            }

            return newContent.toString();
        } catch (Exception e) {
            throw new DriveOperationException("Failed to process file content: " + e.getMessage(), e);
        }
    }

    public boolean verifyPassword(String enteredPassword) throws DriveOperationException {
        try {
            Drive driveService = getReadDriveService();

            String folderId = findPenguinGuardFolderId(driveService)
                    .orElseThrow(() -> new DriveOperationException("PenguinGuard folder not found"));

            String fileId = findPasswordFileId(driveService, folderId)
                    .orElseThrow(() -> new DriveOperationException(fileName + " file not found"));

            return executeWithRetry(() -> {
                try (InputStream fileStream = driveService.files().get(fileId).executeMediaAsInputStream()) {
                    File file = driveService.files().get(fileId).execute();
                    if (file.getSize() != null && file.getSize() > MAX_FILE_SIZE_BYTES) {
                        throw new DriveOperationException("File size exceeds maximum allowed limit");
                    }
                    String storedPassword = converter.convertFromPGuard(fileStream);
                    return storedPassword.equals(Encryption.encrypt(enteredPassword));
                }
            });
        } catch (Exception e) {
            throw new DriveOperationException("Password verification failed", e);
        }
    }

    private <T> T executeWithRetry(DriveOperation<T> operation) throws DriveOperationException {
        int retryCount = 0;
        Exception lastException = null;

        while (retryCount < MAX_RETRIES) {
            try {
                return operation.execute();
            } catch (Exception e) {
                lastException = e;
                retryCount++;
                if (retryCount < MAX_RETRIES) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new DriveOperationException("Operation interrupted during retry", ie);
                    }
                }
            }
        }

        throw new DriveOperationException("Operation failed after " + MAX_RETRIES + " attempts", lastException);
    }

    @FunctionalInterface
    private interface DriveOperation<T> {
        T execute() throws Exception;
    }

    public static class DriveOperationException extends Exception {
        public DriveOperationException(String message) {
            super(message);
        }

        public DriveOperationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}