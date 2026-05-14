package io.github.kurok1.jsonschema.processor;

import javax.annotation.processing.Filer;
import javax.lang.model.element.TypeElement;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Accumulates {@code @JsonSchema} entries across rounds and emits a single
 * lookup class at {@code processingOver()} time.
 *
 * <p>The generated class exposes {@code static String get(Class<?>)} and
 * {@code static Set<Class<?>> registered()}, backed by an immutable map.
 */
final class SchemaRegistryGenerator {

    private final Filer filer;
    private final String registryFqn;
    private final LinkedHashMap<String, String> entries = new LinkedHashMap<>();
    private final List<TypeElement> origins = new ArrayList<>();

    SchemaRegistryGenerator(Filer filer, String registryFqn) {
        this.filer = filer;
        this.registryFqn = registryFqn;
    }

    void register(TypeElement source, String compactJson) {
        String key = source.getQualifiedName().toString();
        if (entries.put(key, compactJson) == null) {
            origins.add(source);
        }
    }

    boolean hasEntries() {
        return !entries.isEmpty();
    }

    void writeRegistryFile() throws IOException {
        int lastDot = registryFqn.lastIndexOf('.');
        String pkg = lastDot >= 0 ? registryFqn.substring(0, lastDot) : "";
        String simple = lastDot >= 0 ? registryFqn.substring(lastDot + 1) : registryFqn;

        String src = renderSource(pkg, simple);
        TypeElement[] originating = origins.toArray(new TypeElement[0]);
        JavaFileObject file = filer.createSourceFile(registryFqn, originating);
        try (Writer w = file.openWriter()) {
            w.write(src);
        }
    }

    private String renderSource(String pkg, String simple) {
        StringBuilder b = new StringBuilder();
        if (!pkg.isEmpty()) {
            b.append("package ").append(pkg).append(";\n\n");
        }
        b.append("import java.util.Collections;\n");
        b.append("import java.util.HashMap;\n");
        b.append("import java.util.Map;\n");
        b.append("import java.util.Set;\n\n");
        b.append("public final class ").append(simple).append(" {\n\n");
        b.append("    private static final Map<Class<?>, String> SCHEMAS;\n\n");
        b.append("    static {\n");
        b.append("        Map<Class<?>, String> m = new HashMap<>(")
                .append(entries.size()).append(");\n");
        for (Map.Entry<String, String> e : entries.entrySet()) {
            b.append("        m.put(").append(e.getKey()).append(".class, \"")
                    .append(JavaStringEscaper.escape(e.getValue())).append("\");\n");
        }
        b.append("        SCHEMAS = Collections.unmodifiableMap(m);\n");
        b.append("    }\n\n");
        b.append("    public static String get(Class<?> type) { return SCHEMAS.get(type); }\n\n");
        b.append("    public static Set<Class<?>> registered() { return SCHEMAS.keySet(); }\n\n");
        b.append("    private ").append(simple).append("() {}\n");
        b.append("}\n");
        return b.toString();
    }
}
