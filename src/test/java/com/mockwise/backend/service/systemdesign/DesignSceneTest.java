package com.mockwise.backend.service.systemdesign;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import com.mockwise.backend.exception.BadRequestException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DesignSceneTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void keepsEveryFieldOnTheDrawing() throws Exception {
        JsonNode node = mapper.readTree("""
                {"version":3,"shapes":[{"type":"box","label":"api","x":12},{"type":"arrow","from":"api"}]}
                """);

        DesignScene scene = DesignScene.require(node, mapper);

        assertTrue(scene.json().contains("\"version\":3"));
        assertTrue(scene.json().contains("\"x\":12"));
        assertTrue(scene.json().contains("\"from\":\"api\""));
        assertEquals(2, DesignScene.shapeCount(scene.json()));
    }

    @Test
    void acceptsAJsonStringThatContainsTheDrawing() throws Exception {
        JsonNode encoded = new TextNode("""
                {"shapes":[{"type":"box","label":"db","w":40}]}
                """);

        DesignScene scene = DesignScene.require(encoded, mapper);

        JsonNode restored = DesignScene.tree(scene.json(), mapper);
        assertEquals("db", restored.get("shapes").get(0).get("label").asText());
        assertEquals(40, restored.get("shapes").get(0).get("w").asInt());
    }

    @Test
    void rejectsTextThatIsNotADrawing() {
        assertThrows(BadRequestException.class, () -> DesignScene.require(new TextNode("not json"), mapper));
        assertThrows(BadRequestException.class, () -> DesignScene.require(mapper.getNodeFactory().numberNode(3), mapper));
        assertThrows(BadRequestException.class, () -> DesignScene.require(null, mapper));
    }

    @Test
    void treeLeavesUnreadableStoredTextAlone() {
        assertNull(DesignScene.tree("not json", mapper));
        assertEquals(0, DesignScene.shapeCount("not json"));
    }
}
