package io.github.kurok1.jsonschema.processor;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import java.util.Map;

/**
 * Helpers for reading annotations by fully-qualified name via {@link AnnotationMirror},
 * so the processor can detect Jackson / JSR-303 / nullness annotations without
 * a compile-time dependency on their declaring artifacts.
 */
public final class Annotations {

    private Annotations() {
    }

    public static AnnotationMirror find(Element element, String fqn) {
        for (AnnotationMirror mirror : element.getAnnotationMirrors()) {
            if (matches(mirror, fqn)) {
                return mirror;
            }
        }
        return null;
    }

    public static AnnotationMirror findAny(Element element, String[] fqns) {
        for (AnnotationMirror mirror : element.getAnnotationMirrors()) {
            for (String fqn : fqns) {
                if (matches(mirror, fqn)) {
                    return mirror;
                }
            }
        }
        return null;
    }

    public static boolean present(Element element, String[] fqns) {
        return findAny(element, fqns) != null;
    }

    public static Object value(Elements elements, AnnotationMirror mirror, String name) {
        Map<? extends ExecutableElement, ? extends AnnotationValue> values =
                elements.getElementValuesWithDefaults(mirror);
        for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> e : values.entrySet()) {
            if (e.getKey().getSimpleName().contentEquals(name)) {
                return e.getValue().getValue();
            }
        }
        return null;
    }

    private static boolean matches(AnnotationMirror mirror, String fqn) {
        TypeElement element = (TypeElement) mirror.getAnnotationType().asElement();
        return fqn.contentEquals(element.getQualifiedName());
    }
}
