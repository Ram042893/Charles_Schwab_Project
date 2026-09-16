package com.schwab.shortener.common;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SimpleJsonTest {

    @Test
    void serializesPrimitivesMapsAndLists() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", "shortener");
        payload.put("enabled", true);
        payload.put("retries", 3);
        payload.put("tags", List.of("jwt", "redis"));
        payload.put("nested", Map.of("ok", true));

        String json = SimpleJson.object(payload);

        assertThat(json).contains("\"name\":\"shortener\"");
        assertThat(json).contains("\"enabled\":true");
        assertThat(json).contains("\"retries\":3");
        assertThat(json).contains("\"tags\":[\"jwt\",\"redis\"]");
        assertThat(json).contains("\"nested\":{\"ok\":true}");
    }

    @Test
    void escapesQuotesAndNewlines() {
        assertThat(SimpleJson.escape("a\"b\\c\nd")).isEqualTo("a\\\"b\\\\c\\nd");
        assertThat(SimpleJson.literal(null)).isEqualTo("null");
    }
}
