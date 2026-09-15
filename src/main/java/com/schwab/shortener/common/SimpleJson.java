package com.schwab.shortener.common;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

public final class SimpleJson {

    private SimpleJson() {
    }

    public static String object(Map<String, ?> values) {
        return values.entrySet().stream()
                .map(entry -> "\"" + escape(entry.getKey()) + "\":" + literal(entry.getValue()))
                .collect(Collectors.joining(",", "{", "}"));
    }

    public static String literal(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> typed = new LinkedHashMap<>();
            map.forEach((k, v) -> typed.put(String.valueOf(k), v));
            return object(typed);
        }
        if (value instanceof Iterable<?> iterable) {
            StringBuilder builder = new StringBuilder("[");
            boolean first = true;
            for (Object item : iterable) {
                if (!first) {
                    builder.append(',');
                }
                builder.append(literal(item));
                first = false;
            }
            return builder.append(']').toString();
        }
        return "\"" + escape(String.valueOf(value)) + "\"";
    }

    public static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}
