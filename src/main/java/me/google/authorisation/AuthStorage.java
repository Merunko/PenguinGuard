package me.google.authorisation;

import me.merunko.utilities.EnvironmentVariablesLoader;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.prefs.Preferences;

public class AuthStorage {
    private static final Preferences prefs = Preferences.userRoot().node("penguin_guard_auth");

    public static void saveSession(String email, String firstName, String refreshToken) {
        if (email == null || firstName == null || refreshToken == null) {
            throw new IllegalArgumentException("Parameters cannot be null");
        }
        prefs.putBoolean("isLoggedIn", true);
        prefs.put("email", email);
        prefs.put("firstName", firstName);
        prefs.put("refreshToken", encrypt(refreshToken));
    }

    public static SessionData loadSession() {
        if (!prefs.getBoolean("isLoggedIn", false)) return null;

        String encryptedToken = prefs.get("refreshToken", "");
        try {
            return new SessionData(
                    prefs.get("firstName", ""),
                    prefs.get("email", ""),
                    encryptedToken.isEmpty() ? "" : decrypt(encryptedToken)
            );
        } catch (RuntimeException e) {
            System.err.println("Failed to load session: " + e.getMessage());
            clearSession();
            return null;
        }
    }

    public static void clearSession() {
        prefs.remove("isLoggedIn");
        prefs.remove("firstName");
        prefs.remove("email");
        prefs.remove("refreshToken");
    }

    private static String encrypt(String plainText) {
        try {
            SecretKeySpec key = generateKey();
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.ENCRYPT_MODE, key);
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new RuntimeException("Encryption failed", e);
        }
    }

    private static String decrypt(String encryptedText) {
        try {
            SecretKeySpec key = generateKey();
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.DECRYPT_MODE, key);
            byte[] decoded = Base64.getDecoder().decode(encryptedText);
            byte[] decrypted = cipher.doFinal(decoded);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Decryption failed", e);
        }
    }

    private static SecretKeySpec generateKey() throws Exception {
        byte[] keyBytes = EnvironmentVariablesLoader.getRefreshTokenSecret().getBytes(StandardCharsets.UTF_8);
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        keyBytes = sha.digest(keyBytes);
        return new SecretKeySpec(keyBytes, "AES");
    }

    public record SessionData(String firstName, String email, String refreshToken) {}
}
