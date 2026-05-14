package io.github.kurok1.jsonschema.annotations;

public enum Draft {
    DRAFT_2020_12("https://json-schema.org/draft/2020-12/schema");

    private final String schemaUri;

    Draft(String schemaUri) {
        this.schemaUri = schemaUri;
    }

    public String schemaUri() {
        return schemaUri;
    }
}
