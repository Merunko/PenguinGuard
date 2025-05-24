package me.merunko.PenguinGuard.Entry;

import com.google.auth.oauth2.GoogleCredentials;
import me.google.drive.DriveService;
import me.merunko.PenguinGuard.security.Converter;
import me.merunko.PenguinGuard.security.Encryption;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class EntryReader {
    private final DriveService driveService;
    private static final String ENTRIES_MARKER = "===ENTRIES===";
    private static final String DELIMITER = "\\|\\|\\|";

    public EntryReader(GoogleCredentials credentials) {
        Objects.requireNonNull(credentials, "GoogleCredentials must not be null");
        Converter converter = new Converter();
        this.driveService = new DriveService(credentials, converter);
    }

    public List<Entry> readAllEntries() throws IOException, DriveService.DriveOperationException {
        try (InputStream inputStream = driveService.downloadFileFromDrive()) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));

            // Skip password line and find entries section
            String line;
            boolean foundEntries = false;
            while ((line = reader.readLine()) != null) {
                if (line.equals(ENTRIES_MARKER)) {
                    foundEntries = true;
                    break;
                }
            }

            if (!foundEntries) {
                return new ArrayList<>();
            }

            // Read entries
            List<Entry> entries = new ArrayList<>();
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }
                try {
                    entries.add(parseEntryLine(line));
                } catch (Exception e) {
                    System.err.println("Skipping invalid entry line: " + line);
                }
            }
            return entries;
        }
    }

    private Entry parseEntryLine(String line) {
        String[] parts = line.split(DELIMITER, -1); // -1 to keep trailing empty strings
        if (parts.length != 7) {
            throw new IllegalArgumentException("Invalid entry format");
        }

        return new Entry(
                parts[0], // ID
                Encryption.decrypt(parts[1]), // category
                Encryption.decrypt(parts[2]), // name
                parts[3].isEmpty() ? "" : Encryption.decrypt(parts[3]), // email
                parts[4].isEmpty() ? "" : Encryption.decrypt(parts[4]), // username
                parts[5].isEmpty() ? "" : Encryption.decrypt(parts[5]), // otherInfo
                Encryption.decrypt(parts[6])  // password
        );
    }
}