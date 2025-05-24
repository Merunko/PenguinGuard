package me.google.authorisation;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class LocalCallbackServer {
    private final GoogleAuthService authService;
    private HttpServer server;
    private static final int SHUTDOWN_DELAY_MS = 2000;
    private final ScheduledExecutorService executor =
            Executors.newSingleThreadScheduledExecutor();
    private volatile boolean running;

    public LocalCallbackServer(GoogleAuthService authService) {
        this.authService = authService;
    }

    public synchronized int start(int port) throws IOException {
        // Ensure any existing server is stopped first
        stop();

        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/Callback", new CallbackHandler());
        server.createContext("/static/", new StaticFileHandler());
        server.setExecutor(Executors.newSingleThreadExecutor());
        server.start();
        running = true;
        System.out.println("Callback server started on port " + port);
        return port;
    }

    public synchronized void stop() {
        if (server != null) {
            try {
                // Schedule immediate shutdown
                server.stop(0);
                System.out.println("Callback server stopped");
            } catch (Exception e) {
                System.err.println("Error stopping callback server: " + e.getMessage());
            } finally {
                server = null;
                running = false;
            }
        }
        executor.shutdownNow();
    }

    public synchronized boolean isRunning() {
        return running;
    }

    private static class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath(); // e.g., "/static/logo.png"
            String resourcePath = path.startsWith("/") ? path : "/" + path;

            try (InputStream is = getClass().getResourceAsStream(resourcePath)) {
                if (is == null) {
                    System.err.println("Static resource not found: " + resourcePath);
                    exchange.sendResponseHeaders(404, -1);
                    return;
                }

                String contentType = guessContentType(resourcePath);
                exchange.getResponseHeaders().set("Content-Type", contentType);

                byte[] bytes = is.readAllBytes();
                exchange.sendResponseHeaders(200, bytes.length);

                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
            }
        }

        private String guessContentType(String path) {
            if (path.endsWith(".png")) return "image/png";
            if (path.endsWith(".jpg") || path.endsWith(".jpeg")) return "image/jpeg";
            if (path.endsWith(".gif")) return "image/gif";
            if (path.endsWith(".css")) return "text/css";
            if (path.endsWith(".js")) return "application/javascript";
            return "application/octet-stream";
        }
    }

    private class CallbackHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String query = exchange.getRequestURI().getQuery();
            String response = buildHtmlResponse();

            if (query != null && query.contains("code=")) {
                String code = URLDecoder.decode(query.split("code=")[1].split("&")[0], StandardCharsets.UTF_8);
                authService.exchangeCodeForTokens(code);
                response = buildSuccessHtmlResponse();
            }

            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, responseBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }

            // Stop the server after handling the callback
            new Thread(() -> {
                try {
                    Thread.sleep(SHUTDOWN_DELAY_MS); // Give time for user to see the message
                    stop();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }).start();
        }

        private String loadTemplate(String filename) {
            try (InputStream is = getClass().getResourceAsStream("/templates/" + filename)) {
                if (is == null) return "<h1>Template Not Found</h1>";
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                return "<h1>Error Loading Template</h1>";
            }
        }

        private String buildHtmlResponse() {
            return loadTemplate("auth_loading.html");
        }

        private String buildSuccessHtmlResponse() {
            return loadTemplate("auth_success.html");
        }
    }
}