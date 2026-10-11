package de.scholtes.georef;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Ein convertLocation-Request je Koordinate.
 * Source ist {@code GeographicalReference-Locations}, Target ist {@code MultiReference-Locations}.
 * Zurückgegeben wird die GeographicalReference-Location jeder Target-Location.
 */
public final class ConvertLocationClient {

    public static final String SOURCE_TYPE = "GeographicalReference-Locations";
    public static final String TARGET_TYPE = "MultiReference-Locations";
    public static final String GEO_LOCATION_TYPE = "GeographicalReference-Location";

    private static final List<String> LAT_FIELDS = List.of("latitude", "lat", "breite");
    private static final List<String> LON_FIELDS = List.of("longitude", "lon", "lng", "laenge", "länge");
    private static final Set<String> SKIP_FIELDS = Set.of(
            "source", "sourcelocation", "sourcelocations", "request", "input");

    private final HttpClient httpClient;
    private final ObjectMapper mapper;
    private final URI endpoint;
    private final String bearerToken;
    private final Duration timeout;
    private final boolean mock;

    public ConvertLocationClient(URI endpoint, String bearerToken, Duration timeout, boolean mock) {
        this(HttpClient.newBuilder().connectTimeout(timeout).build(), new ObjectMapper(),
                endpoint, bearerToken, timeout, mock);
    }

    ConvertLocationClient(
            HttpClient httpClient,
            ObjectMapper mapper,
            URI endpoint,
            String bearerToken,
            Duration timeout,
            boolean mock
    ) {
        this.httpClient = httpClient;
        this.mapper = mapper;
        this.endpoint = endpoint;
        this.bearerToken = bearerToken;
        this.timeout = timeout;
        this.mock = mock;
    }

    public List<GeoPoint> convert(SourceCoordinate coordinate) throws IOException, InterruptedException {
        if (mock) {
            return List.of(new GeoPoint(coordinate.latitude(), coordinate.longitude()));
        }
        if (endpoint == null) {
            throw new IllegalArgumentException(
                    "Kein convertLocation-Endpunkt gesetzt. --endpoint oder --mock angeben.");
        }
        String body = requestBody(coordinate);
        HttpRequest.Builder builder = HttpRequest.newBuilder(endpoint)
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, application/xml, text/xml")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        if (bearerToken != null && !bearerToken.isBlank()) {
            builder.header("Authorization", "Bearer " + bearerToken);
        }
        HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException(
                    "convertLocation für Zeile " + coordinate.excelRow() + " (" + coordinate.role()
                            + ") antwortete mit HTTP " + response.statusCode() + ": "
                            + abbreviate(response.body()));
        }
        List<GeoPoint> points = parseResponse(response.body());
        if (points.isEmpty()) {
            throw new IllegalStateException(
                    "Antwort enthält keine GeographicalReference-Location für Zeile "
                            + coordinate.excelRow() + " (" + coordinate.role() + "): "
                            + abbreviate(response.body()));
        }
        return points;
    }

    public String requestBody(SourceCoordinate coordinate) throws IOException {
        ObjectNode root = mapper.createObjectNode();
        root.put("source", SOURCE_TYPE);
        root.put("target", TARGET_TYPE);
        ObjectNode location = root.putObject("location");
        location.put("type", GEO_LOCATION_TYPE);
        location.put("latitude", coordinate.latitude());
        location.put("longitude", coordinate.longitude());
        return mapper.writeValueAsString(root);
    }

    List<GeoPoint> parseResponse(String body) {
        String trimmed = body == null ? "" : body.strip();
        if (trimmed.isEmpty()) {
            return List.of();
        }
        if (trimmed.charAt(0) == '<') {
            return parseXml(trimmed);
        }
        return parseJson(trimmed);
    }

    private List<GeoPoint> parseJson(String body) {
        try {
            JsonNode root = mapper.readTree(body);
            JsonNode scope = targetScope(root);
            List<GeoPoint> points = new ArrayList<>();
            collectJson(scope, null, points);
            return points;
        } catch (IOException ex) {
            throw new IllegalStateException("convertLocation-Antwort ist kein JSON: " + abbreviate(body), ex);
        }
    }

    private JsonNode targetScope(JsonNode root) {
        if (!root.isObject()) {
            return root;
        }
        for (String name : List.of(
                "targetLocations",
                "targets",
                "target",
                "result",
                "results",
                TARGET_TYPE,
                "multiReferenceLocations",
                "MultiReference-Location")) {
            JsonNode child = root.get(name);
            if (child != null && !child.isNull()) {
                return child;
            }
        }
        return root;
    }

    private void collectJson(JsonNode node, String fieldName, List<GeoPoint> points) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isObject()) {
            if (fieldName != null && SKIP_FIELDS.contains(normalizeKey(fieldName))) {
                return;
            }
            GeoPoint point = asGeoPoint(node, fieldName);
            if (point != null) {
                points.add(point);
                return;
            }
            node.fields().forEachRemaining(entry -> collectJson(entry.getValue(), entry.getKey(), points));
            return;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                collectJson(child, fieldName, points);
            }
        }
    }

    private GeoPoint asGeoPoint(JsonNode node, String fieldName) {
        boolean typed = hasGeoType(node) || isGeoField(fieldName);
        if (!typed) {
            return null;
        }
        Double lat = firstNumber(node, LAT_FIELDS);
        Double lon = firstNumber(node, LON_FIELDS);
        if (lat == null || lon == null) {
            return null;
        }
        return new GeoPoint(lat, lon);
    }

    private boolean hasGeoType(JsonNode node) {
        for (String name : List.of("type", "locationType", "@type")) {
            JsonNode value = node.get(name);
            if (value != null && value.isTextual() && isGeoTypeName(value.asText())) {
                return true;
            }
        }
        return false;
    }

    private Double firstNumber(JsonNode node, List<String> preferredNames) {
        for (String preferred : preferredNames) {
            var fields = node.fields();
            while (fields.hasNext()) {
                var entry = fields.next();
                if (!preferred.equals(normalizeKey(entry.getKey()))) {
                    continue;
                }
                JsonNode value = entry.getValue();
                if (value.isNumber()) {
                    return value.asDouble();
                }
                if (value.isTextual()) {
                    Double parsed = ExcelCoordinateReader.parseNumber(value.asText());
                    if (parsed != null) {
                        return parsed;
                    }
                }
            }
        }
        return null;
    }

    private List<GeoPoint> parseXml(String body) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            var document = factory.newDocumentBuilder()
                    .parse(new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
            List<GeoPoint> points = new ArrayList<>();
            collectXml(document.getDocumentElement(), points);
            return points;
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("convertLocation-Antwort ist kein lesbares XML: " + abbreviate(body), ex);
        }
    }

    private void collectXml(org.w3c.dom.Node node, List<GeoPoint> points) {
        if (node.getNodeType() != org.w3c.dom.Node.ELEMENT_NODE) {
            return;
        }
        String name = normalizeKey(localName(node));
        if (SKIP_FIELDS.contains(name)) {
            return;
        }
        if (isGeoTypeName(localName(node))) {
            Double lat = xmlNumber(node, LAT_FIELDS);
            Double lon = xmlNumber(node, LON_FIELDS);
            if (lat != null && lon != null) {
                points.add(new GeoPoint(lat, lon));
                return;
            }
        }
        var children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            collectXml(children.item(i), points);
        }
    }

    private Double xmlNumber(org.w3c.dom.Node parent, List<String> names) {
        var attributes = parent.getAttributes();
        if (attributes != null) {
            for (int i = 0; i < attributes.getLength(); i++) {
                var attribute = attributes.item(i);
                if (names.contains(normalizeKey(localName(attribute)))) {
                    Double parsed = ExcelCoordinateReader.parseNumber(attribute.getNodeValue());
                    if (parsed != null) {
                        return parsed;
                    }
                }
            }
        }
        var children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            var child = children.item(i);
            if (child.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE
                    && names.contains(normalizeKey(localName(child)))) {
                Double parsed = ExcelCoordinateReader.parseNumber(child.getTextContent());
                if (parsed != null) {
                    return parsed;
                }
            }
        }
        return null;
    }

    private static boolean isGeoField(String fieldName) {
        return fieldName != null && isGeoTypeName(fieldName);
    }

    static boolean isGeoTypeName(String name) {
        String normalized = normalizeKey(name).replace("-", "");
        return normalized.equals("geographicalreferencelocation")
                || normalized.equals("geographicalreference");
    }

    private static String localName(org.w3c.dom.Node node) {
        String name = node.getLocalName() != null ? node.getLocalName() : node.getNodeName();
        int colon = name.indexOf(':');
        return colon >= 0 ? name.substring(colon + 1) : name;
    }

    private static String normalizeKey(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }

    private static String abbreviate(String body) {
        if (body == null) {
            return "";
        }
        String compact = body.replaceAll("\\s+", " ").trim();
        return compact.length() <= 400 ? compact : compact.substring(0, 400) + "...";
    }
}
