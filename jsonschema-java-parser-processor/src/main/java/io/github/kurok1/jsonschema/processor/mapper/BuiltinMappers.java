package io.github.kurok1.jsonschema.processor.mapper;

import io.github.kurok1.jsonschema.processor.AnnotationMapper;

import javax.lang.model.util.Elements;
import java.util.ArrayList;
import java.util.List;

/**
 * Factory for the built-in {@link AnnotationMapper} chain. Keeps the concrete
 * implementations package-private so they remain internal to this subpackage.
 * The order returned here is significant: {@code JsonSchemaSelfMapper} runs
 * first so user-supplied {@code @JsonSchemaProperty} / {@code @JsonSchemaIgnore}
 * intent overrides any conventions inferred from external annotations.
 */
public final class BuiltinMappers {

    private BuiltinMappers() {
    }

    public static List<AnnotationMapper> create(Elements elements,
                                                boolean includeJackson,
                                                boolean includeJsr303,
                                                boolean includeNullness) {
        List<AnnotationMapper> chain = new ArrayList<AnnotationMapper>();
        chain.add(new JsonSchemaSelfMapper());
        if (includeJackson) {
            chain.add(new JacksonMapper(elements));
        }
        if (includeJsr303) {
            chain.add(new ValidationMapper(elements));
        }
        if (includeNullness) {
            chain.add(new NullnessMapper());
        }
        return chain;
    }
}
