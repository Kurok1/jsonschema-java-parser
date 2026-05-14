package io.github.kurok1.jsonschema.core.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A full JSON Schema document: the root node plus dialect URI, optional $id,
 * and a $defs map of named definitions referenced by $ref.
 */
public final class SchemaDocument {

    private final String schemaUri;
    private final String id;
    private final SchemaNode root;
    private final Map<String, SchemaNode> defs;

    private SchemaDocument(Builder b) {
        this.schemaUri = b.schemaUri;
        this.id = b.id;
        this.root = b.root;
        this.defs = Collections.unmodifiableMap(new LinkedHashMap<>(b.defs));
    }

    public String schemaUri() {
        return schemaUri;
    }

    public String id() {
        return id;
    }

    public SchemaNode root() {
        return root;
    }

    public Map<String, SchemaNode> defs() {
        return defs;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String schemaUri;
        private String id;
        private SchemaNode root;
        private final LinkedHashMap<String, SchemaNode> defs = new LinkedHashMap<>();

        public Builder schemaUri(String uri) {
            this.schemaUri = uri;
            return this;
        }

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder root(SchemaNode root) {
            this.root = root;
            return this;
        }

        public Builder addDef(String name, SchemaNode node) {
            this.defs.put(name, node);
            return this;
        }

        public SchemaDocument build() {
            if (root == null) {
                throw new IllegalStateException("SchemaDocument requires a root SchemaNode");
            }
            return new SchemaDocument(this);
        }
    }
}
