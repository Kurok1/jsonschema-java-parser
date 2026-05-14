package io.github.kurok1.jsonschema.processor;

import io.github.kurok1.jsonschema.core.model.SchemaNode;

/**
 * Mutable accumulator shared across {@link AnnotationMapper} invocations for
 * a single field. Holds the property's JSON Schema builder plus naming /
 * required / ignored / optional flags that the mappers progressively decide.
 *
 * <p>SPI mappers receive the same instance the built-in chain mutates — apply
 * your changes via {@link #builder()} or the {@code mark*} / {@code set*} methods.
 */
public final class FieldContext {

    private final String schemaType;
    private final SchemaNode.Builder builder;

    private boolean ignored;
    private boolean required;
    private boolean optional;
    private String nameOverride;

    FieldContext(String schemaType, SchemaNode.Builder builder) {
        this.schemaType = schemaType;
        this.builder = builder;
    }

    /** {@code "string"}, {@code "integer"}, ... or {@code null} for $ref / empty schemas. */
    public String schemaType() {
        return schemaType;
    }

    public SchemaNode.Builder builder() {
        return builder;
    }

    public boolean isIgnored() {
        return ignored;
    }

    public void markIgnored() {
        this.ignored = true;
    }

    public boolean isRequired() {
        return required;
    }

    public void markRequired() {
        this.required = true;
    }

    public boolean isOptional() {
        return optional;
    }

    public void setOptional(boolean optional) {
        this.optional = optional;
    }

    public String nameOverride() {
        return nameOverride;
    }

    public void setNameOverride(String name) {
        this.nameOverride = name;
    }
}
