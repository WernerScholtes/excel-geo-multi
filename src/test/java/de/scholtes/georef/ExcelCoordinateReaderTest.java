package de.scholtes.georef;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExcelCoordinateReaderTest {

    @TempDir
    Path tempDir;

    @Test
    void readsPointColumnsAndIgnoresSectionLength() throws Exception {
        Path excel = tempDir.resolve("strecke.xlsx");
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet("Strecke");
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("Nr");
            header.createCell(1).setCellValue("Abschnittslänge km");
            header.createCell(2).setCellValue("Länge");
            header.createCell(3).setCellValue("Breite");
            var row = sheet.createRow(1);
            row.createCell(0).setCellValue(1);
            row.createCell(1).setCellValue(12.4);
            row.createCell(2).setCellValue(6.083887);
            row.createCell(3).setCellValue(50.775346);
            var textRow = sheet.createRow(2);
            textRow.createCell(0).setCellValue(2);
            textRow.createCell(1).setCellValue("3,5");
            textRow.createCell(2).setCellValue("6,2678");
            textRow.createCell(3).setCellValue("50,8167");
            try (var out = Files.newOutputStream(excel)) {
                workbook.write(out);
            }
        }

        List<SourceCoordinate> points = new ExcelCoordinateReader().read(excel);

        assertEquals(2, points.size());
        assertEquals(2, points.get(0).excelRow());
        assertEquals("punkt", points.get(0).role());
        assertEquals(50.775346, points.get(0).latitude(), 1e-9);
        assertEquals(6.083887, points.get(0).longitude(), 1e-9);
        assertEquals(50.8167, points.get(1).latitude(), 1e-9);
        assertEquals(6.2678, points.get(1).longitude(), 1e-9);
    }

    @Test
    void readsVonAndNachAndIgnoresPlainLength() throws Exception {
        Path excel = tempDir.resolve("abschnitte.xlsx");
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet();
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("Länge");
            header.createCell(1).setCellValue("Länge Von");
            header.createCell(2).setCellValue("Breite Von");
            header.createCell(3).setCellValue("Länge Nach");
            header.createCell(4).setCellValue("Breite Nach");
            var row = sheet.createRow(1);
            row.createCell(0).setCellValue(8.2);
            row.createCell(1).setCellValue(6.10);
            row.createCell(2).setCellValue(50.70);
            row.createCell(3).setCellValue(6.40);
            row.createCell(4).setCellValue(50.80);
            try (var out = Files.newOutputStream(excel)) {
                workbook.write(out);
            }
        }

        List<SourceCoordinate> points = new ExcelCoordinateReader().read(excel);

        assertEquals(List.of("von", "nach"), points.stream().map(SourceCoordinate::role).toList());
        assertEquals(50.70, points.get(0).latitude(), 1e-9);
        assertEquals(6.10, points.get(0).longitude(), 1e-9);
        assertEquals(50.80, points.get(1).latitude(), 1e-9);
        assertEquals(6.40, points.get(1).longitude(), 1e-9);
    }

    @Test
    void rejectsMissingFile() {
        assertThrows(IllegalArgumentException.class,
                () -> new ExcelCoordinateReader().read(tempDir.resolve("fehlt.xlsx")));
    }
}
