package de.scholtes.georef;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

public final class App {

    private final ExcelCoordinateReader reader;
    private final GeoJsonWriter writer;

    public App() {
        this(new ExcelCoordinateReader(), new GeoJsonWriter());
    }

    App(ExcelCoordinateReader reader, GeoJsonWriter writer) {
        this.reader = reader;
        this.writer = writer;
    }

    public int run(AppConfig config) throws IOException, InterruptedException {
        List<SourceCoordinate> sources = reader.read(
                config.excel(), config.latColumn(), config.lonColumn(), config.sheetIndex());
        ConvertLocationClient client = new ConvertLocationClient(
                config.endpoint() == null ? null : URI.create(config.endpoint()),
                config.bearerToken(),
                config.timeout(),
                config.mock());
        List<ConvertedPoint> converted = new ArrayList<>();
        for (SourceCoordinate source : sources) {
            List<GeoPoint> targets = client.convert(source);
            for (int i = 0; i < targets.size(); i++) {
                GeoPoint target = targets.get(i);
                converted.add(new ConvertedPoint(
                        source.excelRow(),
                        source.role(),
                        i,
                        target.latitude(),
                        target.longitude()));
                System.out.printf(
                        "Zeile %d (%s) Target %d: %.6f, %.6f%n",
                        source.excelRow(),
                        source.role(),
                        i,
                        target.latitude(),
                        target.longitude());
            }
        }
        writer.write(config.output(), converted);
        System.out.println("GeoJSON geschrieben: " + config.output().toAbsolutePath()
                + " (" + converted.size() + " Punkte)");
        return converted.size();
    }
}
