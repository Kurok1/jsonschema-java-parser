package io.github.kurok1.jsonschema.processor;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.VariableElement;

/**
 * Strategy that inspects a field's annotations and contributes to its JSON
 * Schema by mutating the supplied {@link FieldContext}.
 *
 * <p>Implementations may be supplied via {@link java.util.ServiceLoader} by
 * adding a {@code META-INF/services/io.github.kurok1.jsonschema.processor.AnnotationMapper}
 * entry on the annotation processor classpath. SPI mappers are required to
 * declare a public no-argument constructor; obtain the {@link javax.lang.model.util.Elements}
 * or {@link javax.lang.model.util.Types} you need from {@link #init(ProcessingEnvironment)}.
 */
public interface AnnotationMapper {

    /**
     * Invoked once per build before {@link #apply(VariableElement, FieldContext)} is called.
     * Default implementation is a no-op; override to capture {@link ProcessingEnvironment}
     * utilities you need at processing time.
     */
    default void init(ProcessingEnvironment env) {
    }

    void apply(VariableElement field, FieldContext ctx);
}
