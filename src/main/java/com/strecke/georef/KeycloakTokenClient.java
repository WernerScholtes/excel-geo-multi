package com.strecke.georef;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class KeycloakTokenClient {
    private final AppConfig config;
    private final ObjectMapper mapper = new ObjectMapper();

    public KeycloakTokenClient(AppConfig config) {
        this.config = config;
    }

    public String fetchAccessToken() throws IOException {
        String tokenUrl = config.require("keycloak.token-url");
        String clientId = config.require("keycloak.client-id");
        String clientSecret = config.require("keycloak.client-secret");
        String scope = config.get("keycloak.scope");

        StringBuilder body = new StringBuilder();
        appendForm(body, "grant_type", "client_credentials");
        appendForm(body, "client_id", clientId);
        appendForm(body, "client_secret", clientSecret);
        if (!scope.isEmpty()) {
            appendForm(body, "scope", scope);
        }

        byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
        HttpURLConnection connection = (HttpURLConnection) new URL(tokenUrl).openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        connection.setRequestProperty("Accept", "application/json");
        try (OutputStream out = connection.getOutputStream()) {
            out.write(payload);
        }

        int status = connection.getResponseCode();
        InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
        JsonNode json = mapper.readTree(stream == null ? "{}".getBytes(StandardCharsets.UTF_8) : readAll(stream));
        if (status >= 400) {
            throw new IOException("Keycloak-Token fehlgeschlagen (" + status + "): " + json);
        }
        JsonNode token = json.get("access_token");
        if (token == null || token.asText().isEmpty()) {
            throw new IOException("Keycloak-Antwort ohne access_token: " + json);
        }
        return token.asText();
    }

    private static void appendForm(StringBuilder body, String name, String value) throws IOException {
        if (body.length() > 0) {
            body.append('&');
        }
        body.append(URLEncoder.encode(name, "UTF-8"));
        body.append('=');
        body.append(URLEncoder.encode(value, "UTF-8"));
    }

    private static byte[] readAll(InputStream stream) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int read;
        while ((read = stream.read(chunk)) != -1) {
            buffer.write(chunk, 0, read);
        }
        return buffer.toByteArray();
    }
}
