package de.scholtes.georef;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConvertLocationClientTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void requestUsesGeographicalSourceAndMultiReferenceTarget() throws Exception {
        ConvertLocationClient client = new ConvertLocationClient(null, null, Duration.ofSeconds(5), true);
        String body = client.requestBody(new SourceCoordinate(4, "von", 50.775346, 6.083887));
        var json = mapper.readTree(body);

        assertEquals(ConvertLocationClient.SOURCE_TYPE, json.get("source").asText());
        assertEquals(ConvertLocationClient.TARGET_TYPE, json.get("target").asText());
        assertEquals(ConvertLocationClient.GEO_LOCATION_TYPE, json.get("location").get("type").asText());
        assertEquals(50.775346, json.get("location").get("latitude").asDouble(), 1e-9);
        assertEquals(6.083887, json.get("location").get("longitude").asDouble(), 1e-9);
    }

    @Test
    void readsGeographicalReferenceOfEveryTargetLocation() {
        ConvertLocationClient client = new ConvertLocationClient(null, null, Duration.ofSeconds(5), true);
        String body = """
                {
                  "source": "GeographicalReference-Locations",
                  "location": {"type": "GeographicalReference-Location", "latitude": 1, "longitude": 2},
                  "targetLocations": [
                    {
                      "type": "MultiReference-Location",
                      "locations": [
                        {"type": "BkmReference-Location", "road": "A4", "kilometer": 12.4},
                        {"type": "GeographicalReference-Location", "latitude": 50.78, "longitude": 6.09}
                      ]
                    },
                    {
                      "type": "MultiReference-Location",
                      "GeographicalReference-Location": {"latitude": "50,79", "longitude": "6,10", "laenge": 1}
                    }
                  ]
                }
                """;

        List<GeoPoint> points = client.parseResponse(body);

        assertEquals(2, points.size());
        assertEquals(50.78, points.get(0).latitude(), 1e-9);
        assertEquals(6.09, points.get(0).longitude(), 1e-9);
        assertEquals(50.79, points.get(1).latitude(), 1e-9);
        assertEquals(6.10, points.get(1).longitude(), 1e-9);
    }

    @Test
    void readsXmlGeographicalReferenceAndSkipsSource() {
        ConvertLocationClient client = new ConvertLocationClient(null, null, Duration.ofSeconds(5), true);
        String body = """
                <convertLocationResponse>
                  <source>
                    <GeographicalReference-Location>
                      <latitude>1.0</latitude>
                      <longitude>2.0</longitude>
                    </GeographicalReference-Location>
                  </source>
                  <targetLocations>
                    <MultiReference-Location>
                      <GeographicalReference-Location latitude="50.81" longitude="6.20"/>
                    </MultiReference-Location>
                  </targetLocations>
                </convertLocationResponse>
                """;

        List<GeoPoint> points = client.parseResponse(body);

        assertEquals(1, points.size());
        assertEquals(50.81, points.get(0).latitude(), 1e-9);
        assertEquals(6.20, points.get(0).longitude(), 1e-9);
    }

    @Test
    void postsOneRequestPerCoordinate() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/convertLocation", exchange -> {
            calls.incrementAndGet();
            String request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            var json = mapper.readTree(request);
            assertEquals("POST", exchange.getRequestMethod());
            assertEquals(ConvertLocationClient.SOURCE_TYPE, json.get("source").asText());
            assertEquals(ConvertLocationClient.TARGET_TYPE, json.get("target").asText());
            double lat = json.get("location").get("latitude").asDouble() + 0.01;
            double lon = json.get("location").get("longitude").asDouble() + 0.02;
            byte[] response = ("""
                    {"targetLocations":[{"type":"MultiReference-Location","locations":[
                      {"type":"GeographicalReference-Location","latitude":%s,"longitude":%s}
                    ]}]}
                    """).formatted(Double.toString(lat), Double.toString(lon)).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            URI endpoint = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/convertLocation");
            ConvertLocationClient client = new ConvertLocationClient(endpoint, "test-token", Duration.ofSeconds(5), false);
            List<GeoPoint> points = client.convert(new SourceCoordinate(3, "punkt", 50.0, 6.0));
            assertEquals(1, calls.get());
            assertEquals(50.01, points.get(0).latitude(), 1e-9);
            assertEquals(6.02, points.get(0).longitude(), 1e-9);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void returnsNoPointWhenTargetHasNoGeographicalReference() {
        ConvertLocationClient client = new ConvertLocationClient(null, null, Duration.ofSeconds(5), true);
        List<GeoPoint> points = client.parseResponse("""
                {"targetLocations":[{"type":"MultiReference-Location","locations":[
                  {"type":"BkmReference-Location","road":"A4"}]}]}
                """);
        assertTrue(points.isEmpty());
    }
}
