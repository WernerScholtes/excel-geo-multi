package de.scholtes.georef;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class GeoJsonWriter {

    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public void write(Path output, List<ConvertedPoint> points) throws IOException {
        ObjectNode root = mapper.createObjectNode();
        root.put("type", "FeatureCollection");
        ArrayNode features = root.putArray("features");
        for (ConvertedPoint point : points) {
            ObjectNode feature = features.addObject();
            feature.put("type", "Feature");
            ObjectNode geometry = feature.putObject("geometry");
            geometry.put("type", "Point");
            ArrayNode coordinates = geometry.putArray("coordinates");
            coordinates.add(point.longitude());
            coordinates.add(point.latitude());
            ObjectNode properties = feature.putObject("properties");
            properties.put("row", point.excelRow());
            properties.put("role", point.role());
            properties.put("targetIndex", point.targetIndex());
        }
        Path parent = output.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        mapper.writeValue(output.toFile(), root);
    }
}
