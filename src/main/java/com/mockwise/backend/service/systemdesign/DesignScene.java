package com.mockwise.backend.service.systemdesign;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockwise.backend.exception.BadRequestException;

/**
 * The drawing submitted for a design session. The tree is kept whole so every field
 * the editor sent can be stored and drawn again.
 */
public final class DesignScene {

    private static final ObjectMapper SHAPES = new ObjectMapper();

    private final String json;

    private DesignScene(String json) {
        this.json = json;
    }

    public static DesignScene require(JsonNode node, ObjectMapper mapper) {
        JsonNode tree = asDocument(node, mapper);
        try {
            return new DesignScene(mapper.writeValueAsString(tree));
        } catch (JsonProcessingException ex) {
            throw new BadRequestException("Scene must be a JSON object or array.");
        }
    }

    /**
     * Parses a stored scene for the feedback response. Returns null when the stored
     * text is not a JSON object or array, so older rows can still be returned as text.
     */
    public static JsonNode tree(String stored, ObjectMapper mapper) {
        if (stored == null || stored.isBlank()) {
            return null;
        }
        try {
            JsonNode node = mapper.readTree(stored);
            if (!node.isContainerNode()) {
                return null;
            }
            return node;
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    public static int shapeCount(String scene) {
        JsonNode tree = tree(scene, SHAPES);
        if (tree == null) {
            return 0;
        }
        return countShapes(tree);
    }

    public String json() {
        return json;
    }

    private static JsonNode asDocument(JsonNode node, ObjectMapper mapper) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            throw new BadRequestException("Scene is required.");
        }
        if (node.isTextual()) {
            String text = node.asText();
            if (text.isBlank()) {
                throw new BadRequestException("Scene must be a JSON object or array.");
            }
            try {
                node = mapper.readTree(text);
            } catch (JsonProcessingException ex) {
                throw new BadRequestException("Scene must be a JSON object or array.");
            }
        }
        if (node == null || !node.isContainerNode()) {
            throw new BadRequestException("Scene must be a JSON object or array.");
        }
        return node;
    }

    private static int countShapes(JsonNode node) {
        int found = 0;
        if (node.isObject() && node.hasNonNull("type")) {
            found++;
        }
        for (JsonNode child : node) {
            found += countShapes(child);
        }
        return found;
    }
}
