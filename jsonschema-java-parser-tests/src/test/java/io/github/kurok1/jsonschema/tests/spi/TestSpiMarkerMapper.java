package io.github.kurok1.jsonschema.tests.spi;

import io.github.kurok1.jsonschema.processor.AnnotationMapper;
import io.github.kurok1.jsonschema.processor.FieldContext;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.VariableElement;

/**
 * Test fixture proving ServiceLoader-discovered mappers are wired into the
 * processor chain. Guarded by the {@code spiTestEnabled} processor option so
 * other test compilations don't see the marker.
 */
public final class TestSpiMarkerMapper implements AnnotationMapper {

    private boolean enabled;

    @Override
    public void init(ProcessingEnvironment env) {
        this.enabled = Boolean.parseBoolean(env.getOptions().getOrDefault("spiTestEnabled", "false"));
    }

    @Override
    public void apply(VariableElement field, FieldContext ctx) {
        if (!enabled) {
            return;
        }
        ctx.builder().keyword("x-spi-marker", "applied");
    }
}
