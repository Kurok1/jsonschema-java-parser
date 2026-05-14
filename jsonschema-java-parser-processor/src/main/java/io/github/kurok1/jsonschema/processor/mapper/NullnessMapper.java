package io.github.kurok1.jsonschema.processor.mapper;

import io.github.kurok1.jsonschema.processor.AnnotationMapper;
import io.github.kurok1.jsonschema.processor.Annotations;
import io.github.kurok1.jsonschema.processor.FieldContext;

import javax.lang.model.element.VariableElement;

/**
 * Marks fields required when any well-known non-null annotation is present.
 * Covers the common ecosystem variants so users don't have to standardise
 * on a single nullness library.
 */
final class NullnessMapper implements AnnotationMapper {

    private static final String[] NON_NULL_FQNS = {
            "javax.annotation.Nonnull",
            "org.jetbrains.annotations.NotNull",
            "lombok.NonNull",
            "org.springframework.lang.NonNull",
            "org.checkerframework.checker.nullness.qual.NonNull",
            "org.jspecify.annotations.NonNull",
            "edu.umd.cs.findbugs.annotations.NonNull"
    };

    @Override
    public void apply(VariableElement field, FieldContext ctx) {
        if (Annotations.findAny(field, NON_NULL_FQNS) != null) {
            ctx.markRequired();
        }
    }
}
