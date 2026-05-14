package io.github.kurok1.jsonschema.core.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable representation of a single JSON Schema node.
 *
 * <p>Heterogeneous keyword values (strings, numbers, lists, nested nodes) are
 * held in an ordered {@link LinkedHashMap}; construct via {@link #builder()}.
 */
public final class SchemaNode {

    private final Map<String, Object> keywords;

    private SchemaNode(Map<String, Object> keywords) {
        this.keywords = Collections.unmodifiableMap(keywords);
    }

    public Map<String, Object> keywords() {
        return keywords;
    }

    public boolean isEmpty() {
        return keywords.isEmpty();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static SchemaNode ref(String ref) {
        return builder().ref(ref).build();
    }

    public static final class Builder {
        private final LinkedHashMap<String, Object> keywords = new LinkedHashMap<>();

        public Builder ref(String ref) {
            keywords.put("$ref", ref);
            return this;
        }

        public Builder type(SchemaType type) {
            keywords.put("type", type.value());
            return this;
        }

        public Builder nullableType(SchemaType type) {
            List<String> types = new ArrayList<>(2);
            types.add(type.value());
            types.add(SchemaType.NULL.value());
            keywords.put("type", types);
            return this;
        }

        public Builder title(String title) {
            if (title != null && !title.isEmpty()) {
                keywords.put("title", title);
            }
            return this;
        }

        public Builder description(String description) {
            if (description != null && !description.isEmpty()) {
                keywords.put("description", description);
            }
            return this;
        }

        public Builder format(String format) {
            if (format != null && !format.isEmpty()) {
                keywords.put("format", format);
            }
            return this;
        }

        public Builder enumValues(List<String> values) {
            if (values != null && !values.isEmpty()) {
                keywords.put("enum", new ArrayList<Object>(values));
            }
            return this;
        }

        public Builder items(SchemaNode items) {
            keywords.put("items", items);
            return this;
        }

        public Builder uniqueItems(boolean unique) {
            if (unique) {
                keywords.put("uniqueItems", Boolean.TRUE);
            }
            return this;
        }

        public Builder properties(Map<String, SchemaNode> properties) {
            if (properties != null && !properties.isEmpty()) {
                keywords.put("properties", new LinkedHashMap<String, Object>(properties));
            }
            return this;
        }

        public Builder required(List<String> required) {
            if (required != null && !required.isEmpty()) {
                keywords.put("required", new ArrayList<Object>(required));
            }
            return this;
        }

        public Builder additionalProperties(boolean allowed) {
            keywords.put("additionalProperties", allowed);
            return this;
        }

        public Builder additionalPropertiesSchema(SchemaNode schema) {
            keywords.put("additionalProperties", schema);
            return this;
        }

        public Builder defaultValue(Object value) {
            if (value != null) {
                keywords.put("default", value);
            }
            return this;
        }

        public Builder examples(List<?> examples) {
            if (examples != null && !examples.isEmpty()) {
                keywords.put("examples", new ArrayList<Object>(examples));
            }
            return this;
        }

        public Builder keyword(String key, Object value) {
            if (key != null && value != null) {
                keywords.put(key, value);
            }
            return this;
        }

        public SchemaNode build() {
            return new SchemaNode(keywords);
        }
    }
}
