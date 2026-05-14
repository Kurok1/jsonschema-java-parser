package io.github.kurok1.jsonschema.processor.mapper;

import io.github.kurok1.jsonschema.annotations.JsonSchemaIgnore;
import io.github.kurok1.jsonschema.annotations.JsonSchemaProperty;
import io.github.kurok1.jsonschema.processor.AnnotationMapper;
import io.github.kurok1.jsonschema.processor.FieldContext;

import javax.lang.model.element.VariableElement;
import java.util.ArrayList;
import java.util.List;

/**
 * Honours this project's own annotations: {@code @JsonSchemaIgnore} and
 * {@code @JsonSchemaProperty}. Always installed first in the mapper chain so
 * that user intent takes precedence over external annotation conventions.
 */
final class JsonSchemaSelfMapper implements AnnotationMapper {

    @Override
    public void apply(VariableElement field, FieldContext ctx) {
        if (field.getAnnotation(JsonSchemaIgnore.class) != null) {
            ctx.markIgnored();
            return;
        }
        JsonSchemaProperty meta = field.getAnnotation(JsonSchemaProperty.class);
        if (meta == null) {
            return;
        }
        if (!meta.name().isEmpty()) {
            ctx.setNameOverride(meta.name());
        }
        if (!meta.description().isEmpty()) {
            ctx.builder().description(meta.description());
        }
        if (!meta.format().isEmpty()) {
            ctx.builder().format(meta.format());
        }
        if (!meta.defaultValue().isEmpty()) {
            ctx.builder().defaultValue(meta.defaultValue());
        }
        if (meta.examples().length > 0) {
            List<Object> examples = new ArrayList<Object>(meta.examples().length);
            for (String example : meta.examples()) {
                examples.add(example);
            }
            ctx.builder().examples(examples);
        }
        if (meta.required()) {
            ctx.markRequired();
        }
    }
}
