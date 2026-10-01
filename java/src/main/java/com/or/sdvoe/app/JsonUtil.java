package com.or.sdvoe.app;

import java.util.Map;

/** Minimal JSON serializer for Map/Iterable responses. */
public final class JsonUtil {

    private JsonUtil() {
    }

    public static String toPrettyJson(Object value) {
        StringBuilder sb = new StringBuilder();
        writeJson(sb, value, 0);
        sb.append('\n');
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static void writeJson(StringBuilder sb, Object value, int indent) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof String s) {
            sb.append('"').append(escape(s)).append('"');
        } else if (value instanceof Number || value instanceof Boolean) {
            sb.append(value);
        } else if (value instanceof Map<?, ?> map) {
            sb.append("{\n");
            int i = 0;
            for (Map.Entry<?, ?> e : map.entrySet()) {
                pad(sb, indent + 2);
                sb.append('"').append(escape(String.valueOf(e.getKey()))).append("\": ");
                writeJson(sb, e.getValue(), indent + 2);
                if (++i < map.size()) {
                    sb.append(',');
                }
                sb.append('\n');
            }
            pad(sb, indent);
            sb.append('}');
        } else if (value instanceof Iterable<?> it) {
            sb.append("[\n");
            int size = 0;
            for (Object ignored : it) {
                size++;
            }
            int i = 0;
            for (Object item : it) {
                pad(sb, indent + 2);
                writeJson(sb, item, indent + 2);
                if (++i < size) {
                    sb.append(',');
                }
                sb.append('\n');
            }
            pad(sb, indent);
            sb.append(']');
        } else {
            sb.append('"').append(escape(String.valueOf(value))).append('"');
        }
    }

    private static void pad(StringBuilder sb, int n) {
        sb.append(" ".repeat(Math.max(0, n)));
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
