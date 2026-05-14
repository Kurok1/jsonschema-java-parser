package io.github.kurok1.jsonschema.core.openai;

import io.github.kurok1.jsonschema.core.model.SchemaDocument;
import io.github.kurok1.jsonschema.core.model.SchemaNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Rewrites a {@link SchemaDocument} into a subset compatible with OpenAI's
 * Structured Outputs / Function Calling strict mode.
 *
 * <p>Applied transformations:
 * <ul>
 *   <li>every object schema has {@code additionalProperties: false}</li>
 *   <li>every {@code properties} entry name is copied into {@code required}</li>
 *   <li>unsupported validation keywords are stripped (min/max length, pattern,
 *       format, numeric bounds, item counts, defaults, examples, allOf, oneOf)</li>
 * </ul>
 *
 * <p>The transformer then validates the result: total property count ≤
 * {@value #MAX_PROPERTIES} and nesting depth ≤ {@value #MAX_DEPTH}, throwing
 * an {@link OpenAiSchemaLimitException} when either is exceeded.
 */
public final class OpenAiCompatibilityTransformer {

    public static final int MAX_PROPERTIES = 100;
    public static final int MAX_DEPTH = 5;

    private static final Set<String> UNSUPPORTED_KEYWORDS;

    static {
        Set<String> s = new HashSet<>();
        s.add("minLength");
        s.add("maxLength");
        s.add("pattern");
        s.add("format");
        s.add("minimum");
        s.add("maximum");
        s.add("exclusiveMinimum");
        s.add("exclusiveMaximum");
        s.add("multipleOf");
        s.add("minItems");
        s.add("maxItems");
        s.add("uniqueItems");
        s.add("minProperties");
        s.add("maxProperties");
        s.add("default");
        s.add("examples");
        s.add("allOf");
        s.add("oneOf");
        UNSUPPORTED_KEYWORDS = Collections.unmodifiableSet(s);
    }

    public SchemaDocument transform(SchemaDocument input) {
        SchemaNode newRoot = transformNode(input.root());
        SchemaDocument.Builder builder = SchemaDocument.builder()
                .schemaUri(input.schemaUri())
                .id(input.id())
                .root(newRoot);
        for (Map.Entry<String, SchemaNode> e : input.defs().entrySet()) {
            builder.addDef(e.getKey(), transformNode(e.getValue()));
        }
        SchemaDocument output = builder.build();
        validateLimits(output);
        return output;
    }

    private SchemaNode transformNode(SchemaNode node) {
        Map<String, Object> source = node.keywords();
        boolean hasProperties = source.containsKey("properties");
        boolean isObject = "object".equals(source.get("type"));

        SchemaNode.Builder b = SchemaNode.builder();
        for (Map.Entry<String, Object> e : source.entrySet()) {
            String key = e.getKey();
            if (UNSUPPORTED_KEYWORDS.contains(key)) {
                continue;
            }
            if ("required".equals(key)) {
                continue;
            }
            Object value = e.getValue();
            if ("properties".equals(key)) {
                b.properties(transformProperties(value));
            } else if ("items".equals(key) && value instanceof SchemaNode) {
                b.items(transformNode((SchemaNode) value));
            } else if ("additionalProperties".equals(key) && value instanceof SchemaNode) {
                if (!hasProperties) {
                    b.additionalPropertiesSchema(transformNode((SchemaNode) value));
                }
            } else if ("additionalProperties".equals(key)) {
                // forced below for objects with properties
            } else if ("anyOf".equals(key) && value instanceof List) {
                b.keyword("anyOf", transformList((List<?>) value));
            } else {
                b.keyword(key, value);
            }
        }

        if (hasProperties) {
            Object rebuilt = b.build().keywords().get("properties");
            @SuppressWarnings("unchecked")
            Map<String, Object> props = (Map<String, Object>) rebuilt;
            b.required(new ArrayList<>(props.keySet()));
            b.additionalProperties(false);
        } else if (isObject && !(source.get("additionalProperties") instanceof SchemaNode)) {
            b.additionalProperties(false);
        }
        return b.build();
    }

    @SuppressWarnings("unchecked")
    private Map<String, SchemaNode> transformProperties(Object value) {
        Map<String, Object> raw = (Map<String, Object>) value;
        Map<String, SchemaNode> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> p : raw.entrySet()) {
            if (p.getValue() instanceof SchemaNode) {
                out.put(p.getKey(), transformNode((SchemaNode) p.getValue()));
            }
        }
        return out;
    }

    private List<Object> transformList(List<?> list) {
        List<Object> out = new ArrayList<>(list.size());
        for (Object item : list) {
            if (item instanceof SchemaNode) {
                out.add(transformNode((SchemaNode) item));
            } else {
                out.add(item);
            }
        }
        return out;
    }

    private void validateLimits(SchemaDocument doc) {
        int totalProperties = countProperties(doc.root());
        for (SchemaNode def : doc.defs().values()) {
            totalProperties += countProperties(def);
        }
        int depth = maxDepth(doc.root(), doc, 0, new HashSet<String>());
        if (totalProperties > MAX_PROPERTIES) {
            throw new OpenAiSchemaLimitException(
                    "OpenAI strict schema exceeds " + MAX_PROPERTIES
                            + " total properties (got " + totalProperties + ")");
        }
        if (depth > MAX_DEPTH) {
            throw new OpenAiSchemaLimitException(
                    "OpenAI strict schema exceeds nesting depth " + MAX_DEPTH
                            + " (got " + depth + ")");
        }
    }

    private int countProperties(SchemaNode node) {
        int total = 0;
        Object propsRaw = node.keywords().get("properties");
        if (propsRaw instanceof Map) {
            Map<?, ?> props = (Map<?, ?>) propsRaw;
            total += props.size();
            for (Object v : props.values()) {
                if (v instanceof SchemaNode) {
                    total += countProperties((SchemaNode) v);
                }
            }
        }
        Object items = node.keywords().get("items");
        if (items instanceof SchemaNode) {
            total += countProperties((SchemaNode) items);
        }
        Object additional = node.keywords().get("additionalProperties");
        if (additional instanceof SchemaNode) {
            total += countProperties((SchemaNode) additional);
        }
        return total;
    }

    private int maxDepth(SchemaNode node, SchemaDocument doc, int current, Set<String> visiting) {
        int max = current;
        Object refVal = node.keywords().get("$ref");
        if (refVal instanceof String) {
            String ref = (String) refVal;
            String prefix = "#/$defs/";
            if (ref.startsWith(prefix)) {
                String key = ref.substring(prefix.length());
                if (visiting.add(key)) {
                    SchemaNode target = doc.defs().get(key);
                    if (target != null) {
                        int d = maxDepth(target, doc, current, visiting);
                        if (d > max) {
                            max = d;
                        }
                    }
                    visiting.remove(key);
                }
            }
            return max;
        }
        Object propsRaw = node.keywords().get("properties");
        if (propsRaw instanceof Map) {
            Map<?, ?> props = (Map<?, ?>) propsRaw;
            for (Object v : props.values()) {
                if (v instanceof SchemaNode) {
                    int d = maxDepth((SchemaNode) v, doc, current + 1, visiting);
                    if (d > max) {
                        max = d;
                    }
                }
            }
        }
        Object items = node.keywords().get("items");
        if (items instanceof SchemaNode) {
            int d = maxDepth((SchemaNode) items, doc, current + 1, visiting);
            if (d > max) {
                max = d;
            }
        }
        Object additional = node.keywords().get("additionalProperties");
        if (additional instanceof SchemaNode) {
            int d = maxDepth((SchemaNode) additional, doc, current + 1, visiting);
            if (d > max) {
                max = d;
            }
        }
        return max;
    }
}
