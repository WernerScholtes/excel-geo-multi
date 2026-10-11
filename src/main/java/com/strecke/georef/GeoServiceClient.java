package com.strecke.georef;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * convertLocations wie excel-bkm-geo.
 * Source-Locations sind GeographicalReference, Target-Type ist MultiReference.
 */
public class GeoServiceClient {
    static final String DEFAULT_TARGET_TYPE = "de.e21x.data.location.MultiReference";
    static final String DEFAULT_LOCATION_TYPE = "de.e21x.data.location.GeographicalReference$Bean";

    private final AppConfig config;
    private final ObjectMapper mapper = new ObjectMapper();

    public GeoServiceClient(AppConfig config) {
        this.config = config;
    }

    public List<JsonNode> convertLocations(String accessToken, List<PointRecord> points) throws IOException {
        byte[] payload = mapper.writeValueAsBytes(requestBody(points));
        String url = trimSlash(config.require("geo.base-url"))
                + (config.get("geo.convert-path").isEmpty() ? "/locations" : config.get("geo.convert-path"));

        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Authorization", "Bearer " + accessToken);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Accept", "application/json");
        try (OutputStream out = connection.getOutputStream()) {
            out.write(payload);
        }

        int status = connection.getResponseCode();
        InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
        JsonNode json = mapper.readTree(stream == null ? "[]".getBytes(StandardCharsets.UTF_8) : readAll(stream));
        if (status >= 400) {
            throw new IOException("convertLocations fehlgeschlagen (" + status + "): " + json);
        }
        if (!json.isArray()) {
            throw new IOException("Unerwartete convertLocations-Antwort: " + json);
        }

        List<JsonNode> result = new ArrayList<JsonNode>();
        for (JsonNode node : json) {
            result.add(node);
        }
        return result;
    }

    public ObjectNode requestBody(List<PointRecord> points) {
        ObjectNode body = mapper.createObjectNode();
        String targetType = config.get("geo.target-type");
        body.put("targetType", targetType.isEmpty() ? DEFAULT_TARGET_TYPE : targetType);
        ArrayNode locations = body.putArray("locations");
        String type = config.get("geo.location-type");
        if (type.isEmpty()) {
            type = DEFAULT_LOCATION_TYPE;
        }
        for (PointRecord point : points) {
            ObjectNode location = locations.addObject();
            location.put("@type", type);
            ObjectNode geometry = location.putObject("geometry");
            geometry.put("type", "Point");
            ArrayNode coordinates = geometry.putArray("coordinates");
            coordinates.add(point.getLongitude());
            coordinates.add(point.getLatitude());
        }
        return body;
    }

    public static List<JsonNode> geographicalGeometries(JsonNode targetLocation) {
        List<JsonNode> geometries = new ArrayList<JsonNode>();
        collect(targetLocation, null, geometries);
        if (geometries.isEmpty()) {
            JsonNode direct = geometryOf(targetLocation);
            if (direct != null && !isError(targetLocation)) {
                geometries.add(direct);
            }
        }
        return geometries;
    }

    public static String errorMessage(JsonNode location) {
        if (location == null) {
            return "leere Antwort";
        }
        JsonNode description = location.get("errorDescription");
        if (description != null && !description.asText().isEmpty()) {
            return description.asText();
        }
        return location.toString();
    }

    private static void collect(JsonNode node, String fieldName, List<JsonNode> geometries) {
        if (node == null || node.isNull() || isError(node)) {
            return;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                collect(child, fieldName, geometries);
            }
            return;
        }
        if (!node.isObject()) {
            return;
        }
        if (isGeographical(text(node, "@type")) || isGeographical(fieldName)) {
            JsonNode geometry = geometryOf(node);
            if (geometry != null) {
                geometries.add(geometry);
                return;
            }
        }
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            if ("geometry".equals(entry.getKey()) || "coordinates".equals(entry.getKey())) {
                continue;
            }
            collect(entry.getValue(), entry.getKey(), geometries);
        }
    }

    private static JsonNode geometryOf(JsonNode location) {
        if (location == null || location.isNull()) {
            return null;
        }
        JsonNode geo = location;
        if (location.has("geo") && location.get("geo").isObject()) {
            geo = location.get("geo");
        }
        JsonNode geometry = geo.get("geometry");
        if (geometry == null || geometry.isNull() || !geometry.isObject()) {
            return null;
        }
        JsonNode coordinates = geometry.get("coordinates");
        if (coordinates == null || !coordinates.isArray() || coordinates.size() < 2) {
            return null;
        }
        return geometry;
    }

    private static boolean isError(JsonNode node) {
        String type = text(node, "@type");
        return type != null && type.contains("LocationError");
    }

    private static boolean isGeographical(String name) {
        return name != null && name.toLowerCase().contains("geographicalreference");
    }

    private static String text(JsonNode node, String field) {
        if (node == null || !node.isObject()) {
            return null;
        }
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static String trimSlash(String value) {
        if (value.endsWith("/")) {
            return value.substring(0, value.length() - 1);
        }
        return value;
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
