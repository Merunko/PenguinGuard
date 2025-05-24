package me.google.authorisation;

import com.google.api.client.googleapis.auth.oauth2.*;
import com.google.api.client.http.ByteArrayContent;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;

import me.merunko.PenguinGuard.user.UserData;
import me.merunko.utilities.EnvironmentVariablesLoader;

import java.awt.*;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class GoogleAuthService {
    private static final String REDIRECT_URI = "http://localhost:8888/Callback";
    private static final int CALLBACK_PORT = 8888;
    private LocalCallbackServer callbackServer;
    private UserData userData;
    private static final List<String> SCOPES = Arrays.asList(
            "openid",
            "email",
            "profile",
            "https://www.googleapis.com/auth/drive.metadata.readonly",
            "https://www.googleapis.com/auth/drive.file"
    );

    public GoogleAuthService() {
        if (EnvironmentVariablesLoader.getGoogleClientId() == null || EnvironmentVariablesLoader.getGoogleClientSecret() == null) {
            throw new IllegalStateException(
                    "Google OAuth credentials not configured. " +
                            "Please set GOOGLE_CLIENT_ID and GOOGLE_CLIENT_SECRET environment variables."
            );
        }
    }

    public interface AuthCompleteListener {
        void onAuthComplete(UserData userData);
    }

    private AuthCompleteListener authCompleteListener;

    public void setAuthCompleteListener(AuthCompleteListener listener) {
        this.authCompleteListener = listener;
    }

    public void initializeWithRefreshToken() {
        AuthStorage.SessionData session = AuthStorage.loadSession();
        if (session == null) return;

        int maxRetries = 3;
        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try {
                GoogleTokenResponse response = new GoogleRefreshTokenRequest(
                        new NetHttpTransport(),
                        new GsonFactory(),
                        session.refreshToken(),
                        EnvironmentVariablesLoader.getGoogleClientId(),
                        EnvironmentVariablesLoader.getGoogleClientSecret()
                ).execute();
                if (response.getAccessToken() == null) throw new IOException("Null access token");

                this.userData = new UserData(session.firstName(),
                        session.email(),
                        response.getAccessToken(),
                        session.refreshToken()
                );

                System.out.println("Token refresh successful");
                return;
            } catch (IOException e) {
                if (attempt == maxRetries - 1) {
                    System.err.println("Token refresh failed after retries: " + e.getMessage());
                    AuthStorage.clearSession();
                }
            }
        }
    }

    public GoogleCredentials getCredentials() {
        if (userData == null) {
            return null;
        }

        return GoogleCredentials.newBuilder()
                .setAccessToken(new AccessToken(userData.accessToken(), null))
                .build();
    }

    public String getRefreshToken() {
        return userData != null ? userData.refreshToken() : null;
    }

    public String getAccessToken() {
        return userData != null ? userData.accessToken() : null;
    }

    public void authenticate() {
        try {
            // Ensure clean state
            stopCallbackServer();

            // Create and start new server
            callbackServer = new LocalCallbackServer(this);
            callbackServer.start(CALLBACK_PORT);

            // URL encode the scopes properly
            String scopeString = SCOPES.stream()
                    .map(scope -> scope.replace(":", "%3A").replace("/", "%2F"))
                    .collect(Collectors.joining("%20"));

            String authUrl = "https://accounts.google.com/o/oauth2/auth?" +
                    "response_type=code&" +
                    "client_id=" + EnvironmentVariablesLoader.getGoogleClientId() + "&" +
                    "redirect_uri=" + REDIRECT_URI + "&" +
                    "scope=" + scopeString + "&" +
                    "access_type=offline&" +
                    "prompt=consent"; // Force consent to get refresh token

            // Open default system browser
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(authUrl));
            } else {
                throw new RuntimeException("Desktop browsing not supported");
            }

        } catch (IOException e) {
            stopCallbackServer(); // Clean up if error occurs

            if (e.getMessage().contains("Address already in use")) {
                // Handle port conflict specifically
                handlePortConflict();
            } else {
                throw new RuntimeException("Failed to start authentication", e);
            }
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    private void handlePortConflict() {
        try {
            stopCallbackServer();
            callbackServer = new LocalCallbackServer(this);
            int assignedPort = callbackServer.start(0); // 0 = auto-assign port
            System.out.println("Using dynamic port: " + assignedPort);
        } catch (IOException e) {
            throw new RuntimeException("Failed to start server on any port", e);
        }
    }

    private GoogleIdToken.Payload getUserInfoFromIdToken(String idToken) throws Exception {
        GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
                new NetHttpTransport(),
                new GsonFactory()
        ).setAudience(Collections.singletonList(EnvironmentVariablesLoader.getGoogleClientId()))
                .build();

        GoogleIdToken token = verifier.verify(idToken);
        if (token == null) {
            throw new SecurityException("Invalid ID token");
        }
        return token.getPayload();
    }

    protected void exchangeCodeForTokens(String code) {
        try {
            GoogleTokenResponse tokenResponse = new GoogleAuthorizationCodeTokenRequest(
                    new NetHttpTransport(),
                    new GsonFactory(),
                    EnvironmentVariablesLoader.getGoogleClientId(),
                    EnvironmentVariablesLoader.getGoogleClientSecret(),
                    code,
                    REDIRECT_URI)
                    .execute();

            // Verify scopes were granted
            if (tokenResponse.getScope() == null ||
                    !tokenResponse.getScope().contains("drive")) {
                throw new IOException("Drive API scopes not granted");
            }

            String idToken = tokenResponse.getIdToken();
            GoogleIdToken.Payload userInfo = getUserInfoFromIdToken(idToken);

            String firstName = String.valueOf(userInfo.get("given_name"));
            String email = String.valueOf(userInfo.get("email"));

            this.userData = new UserData(
                    firstName,
                    email,
                    tokenResponse.getAccessToken(),
                    tokenResponse.getRefreshToken()
            );

            AuthStorage.saveSession(email, firstName, tokenResponse.getRefreshToken());

            if (authCompleteListener != null) {
                authCompleteListener.onAuthComplete(this.userData);
            }

            System.out.println("User data stored: " + userData);

        } catch (IOException e) {
            Platform.runLater(() -> showError("Token Exchange Error", "Failed to exchange code for tokens", e.getMessage()));
        } catch (Exception e) {
            Platform.runLater(() -> showError("Token Decoding Error", "Failed to decode user information", e.getMessage()));
        }
    }

    private boolean isTokenValid(String token) {
        return token != null && !token.isEmpty();
    }

    public synchronized void clearUserData() {
        stopCallbackServer();
        UserData dataToClear = this.userData;
        this.userData = null;

        try {
            if (dataToClear != null && dataToClear.accessToken() != null) {
                revokeToken(dataToClear.accessToken());
            }
        } catch (IOException e) {
            System.err.println("Error revoking token: " + e.getMessage());
        }
    }

    public synchronized void stopCallbackServer() {
        if (callbackServer != null) {
            callbackServer.stop();
            callbackServer = null;
        }
    }

    public void revokeTokens() {
        if (userData == null) return;

        try {
            if (userData.accessToken() != null) {
                revokeToken(userData.accessToken());
            }
            userData = null;
        } catch (IOException e) {
            System.err.println("Error revoking token: " + e.getMessage());
        }
    }

    private void revokeToken(String token) throws IOException {
        if (!isTokenValid(token)) return;

        new NetHttpTransport()
                .createRequestFactory()
                .buildPostRequest(
                        new GenericUrl("https://accounts.google.com/o/oauth2/revoke?token=" + token),
                        new ByteArrayContent("application/x-www-form-urlencoded", new byte[0])
                )
                .execute();
    }

    private void showError(String title, String header, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(message);

        // Add a button to close the dialog
        ButtonType closeButton = new ButtonType("Close", ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(closeButton);

        // Show the dialog and wait for user response
        alert.showAndWait();
    }
}

