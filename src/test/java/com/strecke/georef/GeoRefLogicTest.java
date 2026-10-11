package com.strecke.georef;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class GeoRefLogicTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    public void requestUsesGeographicalSourceAndMultiReferenceTarget() throws Exception {
        AppConfig config = AppConfig.load(new String[0]);
        PointRecord point = new PointRecord(4, "Aachen", "P1", new BigDecimal("50.775346"),
                new BigDecimal("6.083887"), "A4");
        JsonNode body = new GeoServiceClient(config).requestBody(Collections.singletonList(point));

        assertEquals(GeoServiceClient.DEFAULT_TARGET_TYPE, body.get("targetType").asText());
        JsonNode location = body.get("locations").get(0);
        assertEquals(GeoServiceClient.DEFAULT_LOCATION_TYPE, location.get("@type").asText());
        assertEquals("Point", location.get("geometry").get("type").asText());
        assertEquals(new BigDecimal("6.083887"), location.get("geometry").get("coordinates").get(0).decimalValue());
        assertEquals(new BigDecimal("50.775346"), location.get("geometry").get("coordinates").get(1).decimalValue());
    }

    @Test
    public void readsGeographicalReferenceOfEveryTargetLocation() throws Exception {
        JsonNode response = mapper.readTree("["
                + "{\"@type\":\"de.e21x.data.location.MultiReference$Bean\",\"locations\":["
                + "{\"@type\":\"de.e21x.data.location.KilometerReference$Bean\",\"roadNumber\":\"A4\",\"startMeter\":12400},"
                + "{\"@type\":\"de.e21x.data.location.GeographicalReference$Bean\",\"geometry\":{\"type\":\"Point\",\"coordinates\":[6.09,50.78]}}"
                + "]},"
                + "{\"@type\":\"de.e21x.data.location.MultiReference$Bean\",\"locations\":["
                + "{\"@type\":\"de.e21x.data.location.GeographicalReference$Bean\",\"geometry\":{\"type\":\"Point\",\"coordinates\":[6.10,50.79]}}"
                + "]}"
                + "]");

        List<JsonNode> first = GeoServiceClient.geographicalGeometries(response.get(0));
        List<JsonNode> second = GeoServiceClient.geographicalGeometries(response.get(1));

        assertEquals(1, first.size());
        assertEquals(6.09, first.get(0).get("coordinates").get(0).asDouble(), 0.000001);
        assertEquals(50.78, first.get(0).get("coordinates").get(1).asDouble(), 0.000001);
        assertEquals(1, second.size());
        assertEquals(6.10, second.get(0).get("coordinates").get(0).asDouble(), 0.000001);
    }

    @Test
    public void skipsLocationError() throws Exception {
        JsonNode error = mapper.readTree("{\"@type\":\"de.e21x.data.location.LocationError\","
                + "\"errorDescription\":\"nicht gefunden\","
                + "\"geometry\":{\"type\":\"Point\",\"coordinates\":[0,0]}}");
        assertTrue(GeoServiceClient.geographicalGeometries(error).isEmpty());
        assertEquals("nicht gefunden", GeoServiceClient.errorMessage(error));
    }

    @Test
    public void readsSameKonfigurationRowsAsStreckeApp() throws Exception {
        Path dir = Files.createTempDirectory("strecke-excel");
        Path excel = dir.resolve("konfiguration.xlsx");
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Konfiguration");
            sheet.createRow(0).createCell(0).setCellValue("Titel");
            Row header = sheet.createRow(2);
            header.createCell(0).setCellValue("Standort");
            header.createCell(1).setCellValue("PermanentId");
            header.createCell(2).setCellValue("Latitude");
            header.createCell(3).setCellValue("Longitude");
            header.createCell(4).setCellValue("Strecke");
            Row incomplete = sheet.createRow(3);
            incomplete.createCell(2).setCellValue(50.1);
            incomplete.createCell(3).setCellValue(8.1);
            Row row = sheet.createRow(4);
            row.createCell(0).setCellValue("Aachen");
            row.createCell(1).setCellValue("P1");
            row.createCell(2).setCellValue("50,775346");
            row.createCell(3).setCellValue("6,083887");
            row.createCell(4).setCellValue("A4");
            try (java.io.OutputStream out = Files.newOutputStream(excel)) {
                workbook.write(out);
            }
        }

        AppConfig config = AppConfig.load(new String[] { excel.toString() });
        List<PointRecord> points = new ExcelKonfigurationReader(config).read();

        assertEquals(1, points.size());
        assertEquals(5, points.get(0).getExcelRow());
        assertEquals("Aachen", points.get(0).getStandort());
        assertEquals("A4", points.get(0).getStrecke());
        assertEquals(new BigDecimal("50.775346"), points.get(0).getLatitude());
        assertEquals(new BigDecimal("6.083887"), points.get(0).getLongitude());
        Files.deleteIfExists(excel);
        Files.deleteIfExists(dir);
    }

    @Test
    public void parsesGermanDecimal() {
        assertEquals(new BigDecimal("8.123"), DecimalParser.parse("8,123"));
        assertEquals(new BigDecimal("1234.5"), DecimalParser.parse("1.234,5"));
    }

    @Test
    public void writesGeoJsonFromTargetGeometry() throws Exception {
        Path dir = Files.createTempDirectory("strecke-geojson");
        Path file = dir.resolve("target-locations.geojson");
        PointRecord source = new PointRecord(5, "Aachen", "P1", new BigDecimal("50.1"), new BigDecimal("6.1"), "A4");
        JsonNode geometry = mapper.readTree("{\"type\":\"Point\",\"coordinates\":[6.2,50.2]}");
        new GeoJsonWriter().write(file, Arrays.asList(new GeoJsonWriter.TargetFeature(source, 0, geometry)));

        JsonNode written = mapper.readTree(file.toFile());
        assertEquals("FeatureCollection", written.get("type").asText());
        JsonNode feature = written.get("features").get(0);
        assertEquals(6.2, feature.get("geometry").get("coordinates").get(0).asDouble(), 0.000001);
        assertEquals(50.2, feature.get("geometry").get("coordinates").get(1).asDouble(), 0.000001);
        assertEquals("A4", feature.get("properties").get("Strecke").asText());
        Files.deleteIfExists(file);
        Files.deleteIfExists(dir);
    }
}
