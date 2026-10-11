package com.strecke.georef;

import com.fasterxml.jackson.databind.JsonNode;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class ExcelStreckeGeoRefApp {
    public static void main(String[] args) {
        try {
            AppConfig config = AppConfig.load(args);
            List<PointRecord> points = new ExcelKonfigurationReader(config).read();
            System.out.println("Koordinaten: " + points.size());
            if (points.isEmpty()) {
                throw new IllegalArgumentException("Keine Koordinaten im Blatt Konfiguration gefunden.");
            }

            String token = new KeycloakTokenClient(config).fetchAccessToken();
            List<JsonNode> targets = new GeoServiceClient(config).convertLocations(token, points);
            if (targets.size() != points.size()) {
                throw new IllegalStateException("Antwortlaenge " + targets.size()
                        + " weicht von Anfragelaenge " + points.size() + " ab.");
            }

            List<GeoJsonWriter.TargetFeature> features = new ArrayList<GeoJsonWriter.TargetFeature>();
            for (int i = 0; i < points.size(); i++) {
                PointRecord source = points.get(i);
                List<JsonNode> geometries = GeoServiceClient.geographicalGeometries(targets.get(i));
                if (geometries.isEmpty()) {
                    System.out.println("Zeile " + source.getExcelRow() + ": keine GeographicalReference-Location: "
                            + GeoServiceClient.errorMessage(targets.get(i)));
                    continue;
                }
                for (int targetIndex = 0; targetIndex < geometries.size(); targetIndex++) {
                    features.add(new GeoJsonWriter.TargetFeature(source, targetIndex, geometries.get(targetIndex)));
                }
            }

            Path output = resolveOutput(config);
            new GeoJsonWriter().write(output, features);
            System.out.println("GeoJSON geschrieben: " + output.toAbsolutePath() + " (" + features.size() + " Punkte)");
        } catch (Exception ex) {
            System.err.println("Fehler: " + ex.getMessage());
            ex.printStackTrace(System.err);
            System.exit(1);
        }
    }

    static Path resolveOutput(AppConfig config) {
        String configured = config.get("excel.output");
        if (!configured.isEmpty()) {
            return Paths.get(configured);
        }
        Path input = Paths.get(config.require("excel.input"));
        Path parent = input.getParent();
        Path file = Paths.get("geojson").resolve("target-locations.geojson");
        return parent == null ? file : parent.resolve(file);
    }
}
