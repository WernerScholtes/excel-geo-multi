package de.scholtes.georef;

import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

public final class Main {

    public static void main(String[] args) {
        try {
            if (wantsHelp(args)) {
                System.out.println(usage());
                return;
            }
            AppConfig config = parse(args);
            new App().run(config);
        } catch (Exception ex) {
            System.err.println(ex.getMessage() == null ? ex.toString() : ex.getMessage());
            System.err.println();
            System.err.println(usage());
            System.exit(1);
        }
    }

    static AppConfig parse(String[] args) {
        Map<String, String> options = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (!arg.startsWith("--")) {
                throw new IllegalArgumentException("Unbekanntes Argument: " + arg);
            }
            String name = arg.substring(2);
            if ("mock".equals(name) || "help".equals(name)) {
                options.put(name, "true");
                continue;
            }
            if (i + 1 >= args.length || args[i + 1].startsWith("--")) {
                throw new IllegalArgumentException("Wert fehlt für --" + name);
            }
            options.put(name, args[++i]);
        }
        boolean mock = options.containsKey("mock");
        String endpoint = firstNonBlank(options.get("endpoint"), System.getenv("CONVERT_LOCATION_ENDPOINT"));
        if (!mock && (endpoint == null || endpoint.isBlank())) {
            throw new IllegalArgumentException(
                    "convertLocation-Endpunkt fehlt. --endpoint oder CONVERT_LOCATION_ENDPOINT setzen, oder --mock.");
        }
        String excel = options.getOrDefault("excel", "data/strecke.xlsx");
        String output = options.getOrDefault("output", "output/target-locations.geojson");
        String timeoutText = options.getOrDefault("timeout-seconds", "30");
        long timeoutSeconds;
        try {
            timeoutSeconds = Long.parseLong(timeoutText);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("timeout-seconds ist keine Zahl: " + timeoutText);
        }
        Integer sheet = null;
        if (options.containsKey("sheet")) {
            try {
                sheet = Integer.valueOf(options.get("sheet"));
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("sheet ist kein Index: " + options.get("sheet"));
            }
        }
        return new AppConfig(
                Path.of(excel),
                Path.of(output),
                endpoint,
                firstNonBlank(options.get("token"), System.getenv("CONVERT_LOCATION_TOKEN")),
                mock,
                Duration.ofSeconds(timeoutSeconds),
                options.get("lat-column"),
                options.get("lon-column"),
                sheet);
    }

    private static boolean wantsHelp(String[] args) {
        for (String arg : args) {
            if ("--help".equals(arg) || "-h".equals(arg)) {
                return true;
            }
        }
        return false;
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return null;
    }

    static String usage() {
        return """
                excel-strecke-georef

                Liest Koordinaten aus einer Strecken-Excel und wandelt jede Koordinate
                mit einem convertLocation-Request um.
                Source: GeographicalReference-Locations
                Target: MultiReference-Locations
                Ausgabe: GeoJSON der GeographicalReference-Location jeder Target-Location.

                java -jar excel-strecke-georef.jar --excel data/strecke.xlsx --output output/target-locations.geojson --endpoint https://example.test/convertLocation

                Optionen:
                  --excel <datei>         Excel-Datei (Standard: data/strecke.xlsx)
                  --output <datei>        GeoJSON-Ausgabe (Standard: output/target-locations.geojson)
                  --endpoint <url>        convertLocation-Endpunkt (oder CONVERT_LOCATION_ENDPOINT)
                  --token <token>         Bearer-Token (oder CONVERT_LOCATION_TOKEN)
                  --mock                  keine HTTP-Anfrage, Koordinaten unverändert übernehmen
                  --sheet <index>         Blattindex, ab 0 (Standard: 0)
                  --lat-column <name>     Breitenspalte erzwingen
                  --lon-column <name>     Längenspalte erzwingen
                  --timeout-seconds <n>   HTTP-Timeout (Standard: 30)
                  --help                  diese Hilfe
                """;
    }
}
