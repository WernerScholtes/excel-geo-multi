package com.strecke.georef;

import java.math.BigDecimal;

public final class DecimalParser {
    private DecimalParser() {
    }

    public static BigDecimal parse(String raw) {
        if (raw == null) {
            return null;
        }
        String text = raw.trim();
        if (text.isEmpty()) {
            return null;
        }
        if (text.contains(",") && text.contains(".")) {
            text = text.replace(".", "").replace(',', '.');
        } else {
            text = text.replace(',', '.');
        }
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
