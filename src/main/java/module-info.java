module me.merunko.PenguinGuard {
    requires javafx.controls;
    requires java.prefs;
    requires google.api.client;
    requires com.google.api.client;
    requires com.google.api.client.json.gson;
    requires com.google.gson;
    requires jdk.httpserver;
    requires org.slf4j;
    requires com.google.auth.oauth2;
    requires com.google.api.services.drive;
    requires java.datatransfer;
    requires java.desktop;
    requires com.google.auth;

    opens me.merunko.PenguinGuard to javafx.base;
    exports me.merunko.PenguinGuard;
}