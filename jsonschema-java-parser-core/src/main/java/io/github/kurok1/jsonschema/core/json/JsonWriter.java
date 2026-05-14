package io.github.kurok1.jsonschema.core.json;

import io.github.kurok1.jsonschema.core.model.SchemaDocument;
import io.github.kurok1.jsonschema.core.model.SchemaNode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal, zero-dependency JSON writer for {@link SchemaNode} / {@link SchemaDocument}.
 *
 * <p>Handles String, Number, Boolean, null, List, Map, and nested SchemaNode values.
 * Map keys are preserved in their iteration order; callers should pass {@link LinkedHashMap}
 * to keep schema keywords stable.
 */
public final class JsonWriter {

    private static final String INDENT_UNIT = "  ";

    private final boolean pretty;

    public JsonWriter(boolean pretty) {
        this.pretty = pretty;
    }

    public String write(SchemaDocument document) {
        Map<String, Object> root = new LinkedHashMap<>();
        if (document.schemaUri() != null && !document.schemaUri().isEmpty()) {
            root.put("$schema", document.schemaUri());
        }
        if (document.id() != null && !document.id().isEmpty()) {
            root.put("$id", document.id());
        }
        root.putAll(document.root().keywords());
        if (!document.defs().isEmpty()) {
            Map<String, Object> defs = new LinkedHashMap<>();
            for (Map.Entry<String, SchemaNode> e : document.defs().entrySet()) {
                defs.put(e.getKey(), e.getValue());
            }
            root.put("$defs", defs);
        }
        StringBuilder out = new StringBuilder();
        writeObject(root, out, 0);
        return out.toString();
    }

    public String write(SchemaNode node) {
        StringBuilder out = new StringBuilder();
        writeObject(node.keywords(), out, 0);
        return out.toString();
    }

    private void writeValue(Object value, StringBuilder out, int depth) {
        if (value == null) {
            out.append("null");
            return;
        }
        if (value instanceof SchemaNode) {
            writeObject(((SchemaNode) value).keywords(), out, depth);
            return;
        }
        if (value instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) value;
            writeObject(map, out, depth);
            return;
        }
        if (value instanceof List) {
            writeArray((List<?>) value, out, depth);
            return;
        }
        if (value instanceof CharSequence) {
            writeString(value.toString(), out);
            return;
        }
        if (value instanceof Boolean) {
            out.append(((Boolean) value).booleanValue() ? "true" : "false");
            return;
        }
        if (value instanceof Number) {
            writeNumber((Number) value, out);
            return;
        }
        if (value instanceof Enum) {
            writeString(((Enum<?>) value).name(), out);
            return;
        }
        writeString(value.toString(), out);
    }

    private void writeObject(Map<String, Object> map, StringBuilder out, int depth) {
        if (map.isEmpty()) {
            out.append("{}");
            return;
        }
        out.append('{');
        int i = 0;
        int size = map.size();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            newline(out, depth + 1);
            writeString(entry.getKey(), out);
            out.append(':');
            if (pretty) {
                out.append(' ');
            }
            writeValue(entry.getValue(), out, depth + 1);
            if (i < size - 1) {
                out.append(',');
            }
            i++;
        }
        newline(out, depth);
        out.append('}');
    }

    private void writeArray(List<?> list, StringBuilder out, int depth) {
        if (list.isEmpty()) {
            out.append("[]");
            return;
        }
        out.append('[');
        int size = list.size();
        for (int i = 0; i < size; i++) {
            newline(out, depth + 1);
            writeValue(list.get(i), out, depth + 1);
            if (i < size - 1) {
                out.append(',');
            }
        }
        newline(out, depth);
        out.append(']');
    }

    private void writeNumber(Number number, StringBuilder out) {
        if (number instanceof Double || number instanceof Float) {
            double d = number.doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                out.append("null");
                return;
            }
        }
        out.append(number.toString());
    }

    private void writeString(String s, StringBuilder out) {
        out.append('"');
        int len = s.length();
        for (int i = 0; i < len; i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':
                    out.append("\\\"");
                    break;
                case '\\':
                    out.append("\\\\");
                    break;
                case '\n':
                    out.append("\\n");
                    break;
                case '\r':
                    out.append("\\r");
                    break;
                case '\t':
                    out.append("\\t");
                    break;
                case '\b':
                    out.append("\\b");
                    break;
                case '\f':
                    out.append("\\f");
                    break;
                default:
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
            }
        }
        out.append('"');
    }

    private void newline(StringBuilder out, int depth) {
        if (!pretty) {
            return;
        }
        out.append('\n');
        for (int i = 0; i < depth; i++) {
            out.append(INDENT_UNIT);
        }
    }
}
