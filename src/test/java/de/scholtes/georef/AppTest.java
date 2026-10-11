package de.scholtes.georef;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AppTest {

    @TempDir
    Path tempDir;

    @Test
    void writesGeoJsonFromTargetGeographicalReferences() throws Exception {
        Path excel = tempDir.resolve("strecke.xlsx");
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet();
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("Bezeichnung");
            header.createCell(1).setCellValue("Länge");
            header.createCell(2).setCellValue("Breite");
            var row = sheet.createRow(1);
            row.createCell(0).setCellValue("Aachen");
            row.createCell(1).setCellValue(6.083887);
            row.createCell(2).setCellValue(50.775346);
            try (var out = Files.newOutputStream(excel)) {
                workbook.write(out);
            }
        }

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/convertLocation", exchange -> {
            byte[] response = """
                    {"targetLocations":[
                      {"type":"MultiReference-Location","GeographicalReference-Location":{"latitude":50.776,"longitude":6.084}},
                      {"type":"MultiReference-Location","GeographicalReference-Location":{"latitude":50.777,"longitude":6.085}}
                    ]}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            Path output = tempDir.resolve("target-locations.geojson");
            String endpoint = "http://127.0.0.1:" + server.getAddress().getPort() + "/convertLocation";
            int count = new App().run(new AppConfig(
                    excel, output, endpoint, null, false, Duration.ofSeconds(5), null, null, null));

            assertEquals(2, count);
            var geojson = new ObjectMapper().readTree(output.toFile());
            assertEquals("FeatureCollection", geojson.get("type").asText());
            assertEquals(2, geojson.get("features").size());
            var first = geojson.get("features").get(0);
            assertEquals("Point", first.get("geometry").get("type").asText());
            assertEquals(6.084, first.get("geometry").get("coordinates").get(0).asDouble(), 1e-9);
            assertEquals(50.776, first.get("geometry").get("coordinates").get(1).asDouble(), 1e-9);
            assertEquals(2, first.get("properties").get("row").asInt());
            assertEquals("punkt", first.get("properties").get("role").asText());
            assertEquals(0, first.get("properties").get("targetIndex").asInt());
            assertEquals(1, geojson.get("features").get(1).get("properties").get("targetIndex").asInt());
        } finally {
            server.stop(0);
        }
    }
}
