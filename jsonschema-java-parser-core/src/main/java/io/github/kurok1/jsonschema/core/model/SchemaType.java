package io.github.kurok1.jsonschema.core.model;

public enum SchemaType {
    STRING("string"),
    INTEGER("integer"),
    NUMBER("number"),
    BOOLEAN("boolean"),
    OBJECT("object"),
    ARRAY("array"),
    NULL("null");

    private final String value;

    SchemaType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
