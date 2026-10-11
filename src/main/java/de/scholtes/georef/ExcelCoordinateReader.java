package de.scholtes.georef;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Liest aus einer Strecken-Excel nur die Koordinaten.
 * Erkannt werden Spalten wie Breite/Länge, Latitude/Longitude sowie Von/Nach-Paare
 * (Länge Von, Breite Von, Länge Nach, Breite Nach).
 */
public final class ExcelCoordinateReader {

    private static final int HEADER_SCAN_ROWS = 25;

    private final DataFormatter formatter = new DataFormatter(Locale.GERMANY);

    public List<SourceCoordinate> read(Path excel) throws IOException {
        return read(excel, null, null, null);
    }

    public List<SourceCoordinate> read(Path excel, String latColumn, String lonColumn, Integer sheetIndex)
            throws IOException {
        if (!Files.isRegularFile(excel)) {
            throw new IllegalArgumentException("Excel-Datei nicht gefunden: " + excel);
        }
        try (InputStream in = Files.newInputStream(excel);
             Workbook workbook = WorkbookFactory.create(in)) {
            Sheet sheet = selectSheet(workbook, sheetIndex);
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            if (latColumn != null || lonColumn != null) {
                if (latColumn == null || lonColumn == null) {
                    throw new IllegalArgumentException(
                            "Lat- und Lon-Spalte müssen gemeinsam angegeben werden.");
                }
                return readForcedColumns(sheet, evaluator, latColumn, lonColumn);
            }
            return readDetectedColumns(sheet, evaluator);
        }
    }

    private Sheet selectSheet(Workbook workbook, Integer sheetIndex) {
        if (workbook.getNumberOfSheets() == 0) {
            throw new IllegalArgumentException("Die Arbeitsmappe enthält kein Blatt.");
        }
        if (sheetIndex == null) {
            return workbook.getSheetAt(0);
        }
        if (sheetIndex < 0 || sheetIndex >= workbook.getNumberOfSheets()) {
            throw new IllegalArgumentException(
                    "Blattindex " + sheetIndex + " liegt außerhalb von 0.." + (workbook.getNumberOfSheets() - 1));
        }
        return workbook.getSheetAt(sheetIndex);
    }

    private List<SourceCoordinate> readForcedColumns(
            Sheet sheet, FormulaEvaluator evaluator, String latColumn, String lonColumn) {
        int headerRow = findHeaderRow(sheet, evaluator);
        if (headerRow < 0) {
            throw new IllegalArgumentException("Keine Kopfzeile in " + sheet.getSheetName() + " gefunden.");
        }
        Row headers = sheet.getRow(headerRow);
        int latIndex = findColumn(headers, evaluator, latColumn);
        int lonIndex = findColumn(headers, evaluator, lonColumn);
        if (latIndex < 0 || lonIndex < 0) {
            throw new IllegalArgumentException(
                    "Spalten nicht gefunden. latitude=" + latColumn + " (" + latIndex
                            + "), longitude=" + lonColumn + " (" + lonIndex + ")");
        }
        List<SourceCoordinate> points = new ArrayList<>();
        for (int r = headerRow + 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) {
                continue;
            }
            Double lat = readNumber(row.getCell(latIndex), evaluator);
            Double lon = readNumber(row.getCell(lonIndex), evaluator);
            if (lat == null && lon == null) {
                continue;
            }
            points.add(validated(r + 1, "punkt", lat, lon));
        }
        if (points.isEmpty()) {
            throw new IllegalArgumentException("Keine Koordinaten in " + sheet.getSheetName() + " gefunden.");
        }
        return points;
    }

    private List<SourceCoordinate> readDetectedColumns(Sheet sheet, FormulaEvaluator evaluator) {
        int headerRow = findHeaderRow(sheet, evaluator);
        if (headerRow < 0) {
            throw new IllegalArgumentException(
                    "Keine Koordinatenspalten erkannt. Erwartet werden Überschriften wie "
                            + "Breite/Länge oder Länge Von/Breite Von und Länge Nach/Breite Nach.");
        }
        Row headers = sheet.getRow(headerRow);
        Map<String, ColumnPair> pairs = detectPairs(headers, evaluator);
        if (pairs.isEmpty()) {
            throw new IllegalArgumentException(
                    "Keine zusammengehörigen Breiten- und Längenspalten in Zeile " + (headerRow + 1) + ".");
        }
        List<SourceCoordinate> points = new ArrayList<>();
        for (int r = headerRow + 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) {
                continue;
            }
            for (ColumnPair pair : pairs.values()) {
                Double lat = readNumber(row.getCell(pair.latIndex), evaluator);
                Double lon = readNumber(row.getCell(pair.lonIndex), evaluator);
                if (lat == null && lon == null) {
                    continue;
                }
                points.add(validated(r + 1, pair.role, lat, lon));
            }
        }
        if (points.isEmpty()) {
            throw new IllegalArgumentException("Keine Koordinaten in " + sheet.getSheetName() + " gefunden.");
        }
        return points;
    }

    private int findHeaderRow(Sheet sheet, FormulaEvaluator evaluator) {
        int last = Math.min(sheet.getLastRowNum(), HEADER_SCAN_ROWS);
        for (int r = 0; r <= last; r++) {
            Row row = sheet.getRow(r);
            if (row == null) {
                continue;
            }
            if (!detectPairs(row, evaluator).isEmpty()) {
                return r;
            }
        }
        return -1;
    }

    private Map<String, ColumnPair> detectPairs(Row headers, FormulaEvaluator evaluator) {
        Map<String, Integer> latByRole = new LinkedHashMap<>();
        Map<String, Integer> lonByRole = new LinkedHashMap<>();
        boolean specificLongitude = false;
        short last = headers.getLastCellNum();
        for (int c = 0; c < last; c++) {
            String normalized = normalize(cellText(headers.getCell(c), evaluator));
            HeaderMatch match = classify(normalized);
            if (match == null) {
                continue;
            }
            if (match.axis == Axis.LON && !"punkt".equals(match.role)) {
                specificLongitude = true;
            }
            if (match.axis == Axis.LAT) {
                latByRole.putIfAbsent(match.role, c);
            } else {
                lonByRole.putIfAbsent(match.role, c);
            }
        }
        if (specificLongitude) {
            lonByRole.remove("punkt");
        }
        Map<String, ColumnPair> pairs = new LinkedHashMap<>();
        for (String role : List.of("von", "nach", "punkt")) {
            Integer lat = latByRole.get(role);
            Integer lon = lonByRole.get(role);
            if (lat != null && lon != null) {
                pairs.put(role, new ColumnPair(role, lat, lon));
            }
        }
        return pairs;
    }

    private int findColumn(Row headers, FormulaEvaluator evaluator, String wanted) {
        String needle = normalize(wanted);
        short last = headers.getLastCellNum();
        for (int c = 0; c < last; c++) {
            if (needle.equals(normalize(cellText(headers.getCell(c), evaluator)))) {
                return c;
            }
        }
        return -1;
    }

    private SourceCoordinate validated(int excelRow, String role, Double lat, Double lon) {
        if (lat == null || lon == null) {
            throw new IllegalArgumentException(
                    "Unvollständige Koordinate in Zeile " + excelRow + " (" + role + ").");
        }
        if (lat < -90 || lat > 90 || lon < -180 || lon > 180) {
            throw new IllegalArgumentException(
                    "Koordinaten außerhalb des Wertebereichs in Zeile " + excelRow
                            + ": Breite=" + lat + ", Länge=" + lon);
        }
        return new SourceCoordinate(excelRow, role, lat, lon);
    }

    private Double readNumber(Cell cell, FormulaEvaluator evaluator) {
        if (cell == null) {
            return null;
        }
        return switch (cell.getCellType()) {
            case NUMERIC -> cell.getNumericCellValue();
            case BOOLEAN, BLANK, ERROR -> null;
            case FORMULA -> switch (cell.getCachedFormulaResultType()) {
                case NUMERIC -> cell.getNumericCellValue();
                case STRING -> parseNumber(cell.getStringCellValue());
                default -> parseNumber(cellText(cell, evaluator));
            };
            case STRING -> parseNumber(cell.getStringCellValue());
            default -> parseNumber(cellText(cell, evaluator));
        };
    }

    private String cellText(Cell cell, FormulaEvaluator evaluator) {
        if (cell == null) {
            return "";
        }
        return formatter.formatCellValue(cell, evaluator).trim();
    }

    static Double parseNumber(String raw) {
        if (raw == null) {
            return null;
        }
        String text = raw.trim().replace(" ", "").replace("\u00a0", "");
        if (text.isEmpty() || text.equals("-")) {
            return null;
        }
        if (text.contains(",") && text.contains(".")) {
            if (text.lastIndexOf(',') > text.lastIndexOf('.')) {
                text = text.replace(".", "").replace(',', '.');
            } else {
                text = text.replace(",", "");
            }
        } else if (text.contains(",")) {
            text = text.replace(',', '.');
        }
        try {
            return Double.valueOf(text);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    static String normalize(String header) {
        if (header == null) {
            return "";
        }
        String value = header.trim().toLowerCase(Locale.ROOT)
                .replace("ä", "ae")
                .replace("ö", "oe")
                .replace("ü", "ue")
                .replace("ß", "ss");
        StringBuilder builder = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (Character.isLetterOrDigit(ch)) {
                builder.append(ch);
            }
        }
        return builder.toString();
    }

    static HeaderMatch classify(String normalized) {
        return switch (normalized) {
            case "breite", "latitude", "lat" -> new HeaderMatch(Axis.LAT, "punkt");
            case "laenge", "longitude", "lon", "lng", "long" -> new HeaderMatch(Axis.LON, "punkt");
            case "breitevon", "latvon", "latitudevon", "startbreite", "startlat", "startlatitude",
                    "vonbreite", "vonlat", "vonlatitude" -> new HeaderMatch(Axis.LAT, "von");
            case "laengevon", "lonvon", "lngvon", "longitudevon", "startlaenge", "startlon",
                    "startlng", "startlongitude", "vonlaenge", "vonlon", "vonlng", "vonlongitude" ->
                    new HeaderMatch(Axis.LON, "von");
            case "breitenach", "latnach", "latbis", "latitudenach", "endbreite", "endlat",
                    "endlatitude", "nachbreite", "bisbreite", "nachlat", "bislat" ->
                    new HeaderMatch(Axis.LAT, "nach");
            case "laengenach", "lonnach", "lngnach", "longitudenach", "lonbis", "endlaenge",
                    "endlon", "endlng", "endlongitude", "nachlaenge", "bislaenge", "nachlon", "bislon" ->
                    new HeaderMatch(Axis.LON, "nach");
            default -> null;
        };
    }

    enum Axis {
        LAT, LON
    }

    record HeaderMatch(Axis axis, String role) {
    }

    private record ColumnPair(String role, int latIndex, int lonIndex) {
    }
}
