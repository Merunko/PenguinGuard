package me.merunko.PenguinGuard.security;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class Converter {
    private static final int FILE_VERSION = 2; // Increment version for new format
    protected static final String FILE_MAGIC = System.getenv("FILE_MAGIC");
    private static final byte[] ENTRIES_MARKER = new byte[]{0x1D, 0x2E, 0x3F}; // Binary marker

    public byte[] convertToPGuard(String password) throws IOException {
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(byteStream)) {
            // Write file header
            out.write(FILE_MAGIC.getBytes(StandardCharsets.US_ASCII));
            out.writeInt(FILE_VERSION);

            // Write encrypted password (binary format)
            byte[] encryptedPassword = Encryption.encrypt(password).getBytes(StandardCharsets.UTF_8);
            out.writeInt(encryptedPassword.length);
            out.write(encryptedPassword);

            // Write binary entries marker
            out.write(ENTRIES_MARKER);

            return byteStream.toByteArray();
        }
    }

    public String convertFromPGuard(InputStream inputStream) throws IOException {
        try (DataInputStream in = new DataInputStream(inputStream)) {
            // Read and verify magic number
            byte[] magicBytes = new byte[6];
            in.readFully(magicBytes);
            if (!FILE_MAGIC.equals(new String(magicBytes, StandardCharsets.US_ASCII))) {
                throw new IOException("Invalid file format - missing magic number");
            }

            // Read version
            int version = in.readInt();
            if (version != FILE_VERSION) {
                throw new IOException("Unsupported file version: " + version);
            }

            // Read password
            int passwordLength = in.readInt();
            byte[] passwordBytes = new byte[passwordLength];
            in.readFully(passwordBytes);

            return new String(passwordBytes, StandardCharsets.UTF_8);
        }
    }

    public byte[] appendEntry(byte[] existingContent, byte[] newEntry) throws IOException {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             DataOutputStream out = new DataOutputStream(outputStream)) {

            // Write existing content
            out.write(existingContent);

            // Append new entry (already encrypted)
            out.write(newEntry);
            out.write(System.lineSeparator().getBytes(StandardCharsets.UTF_8));

            return outputStream.toByteArray();
        }
    }

    public String extractEntriesContent(InputStream inputStream) throws IOException {
        DataInputStream dataIn = getDataInputStream(inputStream);

        // Read remaining content
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] data = new byte[1024];
        int bytesRead;
        while ((bytesRead = dataIn.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, bytesRead);
        }

        String content = buffer.toString(StandardCharsets.UTF_8);
        int entriesStart = content.indexOf("===ENTRIES===");
        return entriesStart == -1 ? "" : content.substring(entriesStart + "===ENTRIES===".length()).trim();
    }

    private static DataInputStream getDataInputStream(InputStream inputStream) throws IOException {
        DataInputStream dataIn = new DataInputStream(inputStream);

        // Verify file header
        byte[] magicBytes = new byte[6];
        dataIn.readFully(magicBytes);
        if (!FILE_MAGIC.equals(new String(magicBytes, StandardCharsets.US_ASCII))) {
            throw new IOException("Invalid file format - missing magic number");
        }

        // Verify version
        int version = dataIn.readInt();
        if (version != FILE_VERSION) {
            throw new IOException("Unsupported file version: " + version);
        }

        // Skip password section
        int passwordLength = dataIn.readInt();
        dataIn.skipBytes(passwordLength);
        return dataIn;
    }
}