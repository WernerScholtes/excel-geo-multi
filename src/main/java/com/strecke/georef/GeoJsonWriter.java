package com.strecke.georef;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class GeoJsonWriter {
    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public void write(Path file, List<TargetFeature> features) throws IOException {
        ObjectNode collection = mapper.createObjectNode();
        collection.put("type", "FeatureCollection");
        ArrayNode featureArray = collection.putArray("features");
        for (TargetFeature feature : features) {
            ObjectNode node = featureArray.addObject();
            node.put("type", "Feature");
            node.set("geometry", feature.getGeometry());
            ObjectNode properties = node.putObject("properties");
            properties.put("Standort", feature.getSource().getStandort());
            properties.put("PermanentId", feature.getSource().getPermanentId());
            properties.put("Strecke", feature.getSource().getStrecke());
            properties.put("row", feature.getSource().getExcelRow());
            properties.put("targetIndex", feature.getTargetIndex());
        }
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        mapper.writeValue(file.toFile(), collection);
    }

    public static final class TargetFeature {
        private final PointRecord source;
        private final int targetIndex;
        private final JsonNode geometry;

        public TargetFeature(PointRecord source, int targetIndex, JsonNode geometry) {
            this.source = source;
            this.targetIndex = targetIndex;
            this.geometry = geometry;
        }

        public PointRecord getSource() {
            return source;
        }

        public int getTargetIndex() {
            return targetIndex;
        }

        public JsonNode getGeometry() {
            return geometry;
        }
    }
}
