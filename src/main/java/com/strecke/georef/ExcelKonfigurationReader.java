package com.strecke.georef;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Liest dasselbe Blatt {@code Konfiguration} wie excel-strecke-geojson.
 * Fuer convertLocations werden nur Latitude und Longitude verwendet.
 */
public class ExcelKonfigurationReader {
    private final AppConfig config;
    private final DataFormatter formatter = new DataFormatter();

    public ExcelKonfigurationReader(AppConfig config) {
        this.config = config;
    }

    public List<PointRecord> read() throws IOException {
        Path input = Paths.get(config.require("excel.input"));
        if (!Files.isRegularFile(input)) {
            throw new IOException("Excel-Datei nicht gefunden: " + input.toAbsolutePath());
        }

        Workbook workbook;
        try {
            workbook = WorkbookFactory.create(input.toFile());
        } catch (Exception ex) {
            throw new IOException("Excel-Datei konnte nicht gelesen werden: " + input.toAbsolutePath(), ex);
        }

        try {
            String sheetName = config.get("excel.sheet");
            if (sheetName.isEmpty()) {
                sheetName = "Konfiguration";
            }
            Sheet sheet = workbook.getSheet(sheetName);
            if (sheet == null) {
                throw new IllegalArgumentException("Blatt nicht gefunden: " + sheetName);
            }

            int headerExcelRow = config.getInt("excel.header.row", 3);
            int headerIndex = headerExcelRow - 1;
            Row headerRow = sheet.getRow(headerIndex);
            if (headerRow == null) {
                throw new IllegalArgumentException("Keine Kopfzeile in Zeile " + headerExcelRow);
            }

            Map<String, Integer> headers = readHeaders(headerRow);
            int standort = requireColumn(headers, config.get("excel.column.standort"), "Standort");
            int permanentId = requireColumn(headers, config.get("excel.column.permanent-id"), "PermanentId");
            int latitude = requireColumn(headers, config.get("excel.column.latitude"), "Latitude");
            int longitude = requireColumn(headers, config.get("excel.column.longitude"), "Longitude");
            int strecke = requireColumn(headers, config.get("excel.column.strecke"), "Strecke");

            List<PointRecord> records = new ArrayList<PointRecord>();
            int last = sheet.getLastRowNum();
            for (int rowIndex = headerIndex + 1; rowIndex <= last; rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) {
                    continue;
                }
                String standortValue = cellText(row, standort);
                String permanentIdValue = cellText(row, permanentId);
                String latitudeRaw = cellText(row, latitude);
                String longitudeRaw = cellText(row, longitude);
                String streckeValue = cellText(row, strecke);
                if (standortValue.isEmpty() || permanentIdValue.isEmpty() || latitudeRaw.isEmpty()
                        || longitudeRaw.isEmpty() || streckeValue.isEmpty()) {
                    continue;
                }
                BigDecimal lat = DecimalParser.parse(latitudeRaw);
                BigDecimal lon = DecimalParser.parse(longitudeRaw);
                if (lat == null || lon == null) {
                    System.out.println("Zeile " + (rowIndex + 1) + ": Latitude/Longitude ungueltig, uebersprungen.");
                    continue;
                }
                records.add(new PointRecord(rowIndex + 1, standortValue, permanentIdValue, lat, lon, streckeValue));
            }
            return records;
        } finally {
            workbook.close();
        }
    }

    private Map<String, Integer> readHeaders(Row headerRow) {
        Map<String, Integer> headers = new HashMap<String, Integer>();
        short lastCell = headerRow.getLastCellNum();
        for (int col = 0; col < lastCell; col++) {
            Cell cell = headerRow.getCell(col);
            if (cell == null) {
                continue;
            }
            String text = formatter.formatCellValue(cell).trim();
            if (!text.isEmpty()) {
                headers.put(normalize(text), col);
            }
        }
        return headers;
    }

    private int requireColumn(Map<String, Integer> headers, String configured, String fallback) {
        String name = configured.isEmpty() ? fallback : configured;
        Integer column = find(headers, name);
        if (column == null && !name.equals(fallback)) {
            column = find(headers, fallback);
        }
        if (column == null) {
            throw new IllegalArgumentException("Spalte nicht gefunden: " + name);
        }
        return column.intValue();
    }

    private static Integer find(Map<String, Integer> headers, String name) {
        return headers.get(normalize(name));
    }

    private static String normalize(String value) {
        return value.trim().toLowerCase().replace(":", "").replaceAll("\\s+", " ");
    }

    private String cellText(Row row, int column) {
        Cell cell = row.getCell(column);
        if (cell == null) {
            return "";
        }
        CellType type = cell.getCellType();
        if (type == CellType.FORMULA) {
            type = cell.getCachedFormulaResultType();
        }
        switch (type) {
            case STRING:
                return cell.getRichStringCellValue().getString().trim();
            case NUMERIC:
                return formatter.formatRawCellContents(
                        cell.getNumericCellValue(),
                        cell.getCellStyle().getDataFormat(),
                        cell.getCellStyle().getDataFormatString()).trim();
            case BOOLEAN:
                return Boolean.toString(cell.getBooleanCellValue());
            case ERROR:
            case BLANK:
            case _NONE:
                return "";
            default:
                return formatter.formatCellValue(cell).trim();
        }
    }
}
