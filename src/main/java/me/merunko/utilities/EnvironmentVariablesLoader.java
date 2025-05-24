package me.merunko.utilities;

public final class EnvironmentVariablesLoader {

    private EnvironmentVariablesLoader() {}

    public static String getApplicationName() {
        return System.getenv("APPLICATION_NAME");
    }

    public static String getEncryption() {
        return System.getenv("ENCRYPTION");
    }

    public static String getFileMagic() {
        return System.getenv("FILE_MAGIC");
    }

    public static String getFileName() {
        return System.getenv("FILE_NAME");
    }

    public static String getFolderMimeType() {
        return System.getenv("FOLDER_MIME_TYPE");
    }

    public static String getGoogleClientId() {
        return System.getenv("GOOGLE_CLIENT_ID");
    }

    public static String getGoogleClientSecret() {
        return System.getenv("GOOGLE_CLIENT_SECRET");
    }

    public static String getRefreshTokenSecret() {
        return System.getenv("REFRESH_TOKEN_SECRET");
    }
}
