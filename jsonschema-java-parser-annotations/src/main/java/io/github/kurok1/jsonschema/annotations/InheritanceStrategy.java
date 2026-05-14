package io.github.kurok1.jsonschema.annotations;

public enum InheritanceStrategy {
    /** Inline parent fields into the child schema. */
    FLATTEN,
    /** Emit allOf with a $ref to the parent schema. */
    ALL_OF
}
