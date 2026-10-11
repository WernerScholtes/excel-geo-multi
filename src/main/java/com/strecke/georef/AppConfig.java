package com.strecke.georef;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

public class AppConfig {
    private final Properties properties = new Properties();

    public static AppConfig load(String[] args) throws IOException {
        AppConfig config = new AppConfig();
        try (InputStream in = AppConfig.class.getResourceAsStream("/application.properties")) {
            if (in != null) {
                loadUtf8(config.properties, in);
            }
        }
        Path local = Paths.get("application.properties");
        if (Files.isRegularFile(local)) {
            try (InputStream in = Files.newInputStream(local)) {
                loadUtf8(config.properties, in);
            }
        }
        if (args != null && args.length > 0) {
            config.properties.setProperty("excel.input", args[0]);
        }
        if (args != null && args.length > 1) {
            config.properties.setProperty("excel.output", args[1]);
        }
        return config;
    }

    public String get(String key) {
        String value = properties.getProperty(key);
        return value == null ? "" : value.trim();
    }

    public String require(String key) {
        String value = get(key);
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Pflichtwert fehlt: " + key);
        }
        return value;
    }

    public int getInt(String key, int defaultValue) {
        String value = get(key);
        if (value.isEmpty()) {
            return defaultValue;
        }
        return Integer.parseInt(value);
    }

    private static void loadUtf8(Properties properties, InputStream in) throws IOException {
        try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
    }
}
