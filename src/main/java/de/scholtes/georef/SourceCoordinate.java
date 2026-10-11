package de.scholtes.georef;

/**
 * Eine Koordinate aus der Strecken-Excel. {@code excelRow} ist die 1-basierte Zeilennummer.
 * {@code role} ist {@code punkt}, {@code von} oder {@code nach}.
 */
public record SourceCoordinate(int excelRow, String role, double latitude, double longitude) {
}
