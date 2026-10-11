package com.strecke.georef;

import java.math.BigDecimal;

public class PointRecord {
    private final int excelRow;
    private final String standort;
    private final String permanentId;
    private final BigDecimal latitude;
    private final BigDecimal longitude;
    private final String strecke;

    public PointRecord(int excelRow, String standort, String permanentId, BigDecimal latitude, BigDecimal longitude,
            String strecke) {
        this.excelRow = excelRow;
        this.standort = standort;
        this.permanentId = permanentId;
        this.latitude = latitude;
        this.longitude = longitude;
        this.strecke = strecke;
    }

    public int getExcelRow() {
        return excelRow;
    }

    public String getStandort() {
        return standort;
    }

    public String getPermanentId() {
        return permanentId;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public String getStrecke() {
        return strecke;
    }
}
