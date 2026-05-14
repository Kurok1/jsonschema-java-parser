package io.github.kurok1.jsonschema.processor.mapper;

import io.github.kurok1.jsonschema.processor.AnnotationMapper;
import io.github.kurok1.jsonschema.processor.Annotations;
import io.github.kurok1.jsonschema.processor.FieldContext;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.Elements;

/**
 * Maps a subset of Jackson annotations to JSON Schema concepts:
 * <ul>
 *   <li>{@code @JsonIgnore} → skip field</li>
 *   <li>{@code @JsonProperty(value)} → property rename</li>
 *   <li>{@code @JsonProperty(required = true)} → add to required list</li>
 *   <li>{@code @JsonProperty(defaultValue)} → {@code default} keyword</li>
 *   <li>{@code @JsonPropertyDescription} → {@code description}</li>
 * </ul>
 */
final class JacksonMapper implements AnnotationMapper {

    private static final String JSON_IGNORE = "com.fasterxml.jackson.annotation.JsonIgnore";
    private static final String JSON_PROPERTY = "com.fasterxml.jackson.annotation.JsonProperty";
    private static final String JSON_PROPERTY_DESCRIPTION =
            "com.fasterxml.jackson.annotation.JsonPropertyDescription";

    private final Elements elements;

    JacksonMapper(Elements elements) {
        this.elements = elements;
    }

    @Override
    public void apply(VariableElement field, FieldContext ctx) {
        AnnotationMirror ignore = Annotations.find(field, JSON_IGNORE);
        if (ignore != null) {
            Object value = Annotations.value(elements, ignore, "value");
            if (!Boolean.FALSE.equals(value)) {
                ctx.markIgnored();
                return;
            }
        }
        AnnotationMirror property = Annotations.find(field, JSON_PROPERTY);
        if (property != null) {
            Object name = Annotations.value(elements, property, "value");
            if (name instanceof String && !((String) name).isEmpty()) {
                ctx.setNameOverride((String) name);
            }
            Object required = Annotations.value(elements, property, "required");
            if (Boolean.TRUE.equals(required)) {
                ctx.markRequired();
            }
            Object defaultValue = Annotations.value(elements, property, "defaultValue");
            if (defaultValue instanceof String && !((String) defaultValue).isEmpty()) {
                ctx.builder().defaultValue(defaultValue);
            }
        }
        AnnotationMirror description = Annotations.find(field, JSON_PROPERTY_DESCRIPTION);
        if (description != null) {
            Object value = Annotations.value(elements, description, "value");
            if (value instanceof String && !((String) value).isEmpty()) {
                ctx.builder().description((String) value);
            }
        }
    }
}
