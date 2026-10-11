package de.scholtes.georef;

import java.nio.file.Path;
import java.time.Duration;

public record AppConfig(
    Path excel,
    Path output,
    String endpoint,
    String bearerToken,
    boolean mock,
    Duration timeout,
    String latColumn,
    String lonColumn,
    Integer sheetIndex
) {
}
