package de.scholtes.georef;

/**
 * Geografische Lage einer Target-Location nach convertLocation.
 * {@code targetIndex} zählt die Target-Locations eines einzelnen Requests ab 0.
 */
public record ConvertedPoint(
    int excelRow,
    String role,
    int targetIndex,
    double latitude,
    double longitude
) {
}
