package io.github.kurok1.jsonschema.processor;

import javax.annotation.processing.Filer;
import javax.lang.model.element.TypeElement;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.Writer;

/**
 * Emits a Java source file alongside each {@code @JsonSchema} class with the
 * compact JSON schema baked in as a {@code public static final String JSON}.
 * Naming convention: {@code <pkg>.<SimpleName>JsonSchema}.
 */
final class ConstantsClassGenerator {

    private final Filer filer;

    ConstantsClassGenerator(Filer filer) {
        this.filer = filer;
    }

    void generate(TypeElement source, String compactJson) throws IOException {
        String fqn = source.getQualifiedName().toString();
        int lastDot = fqn.lastIndexOf('.');
        String pkg = lastDot >= 0 ? fqn.substring(0, lastDot) : "";
        String simpleName = (lastDot >= 0 ? fqn.substring(lastDot + 1) : fqn) + "JsonSchema";
        String generatedFqn = pkg.isEmpty() ? simpleName : pkg + "." + simpleName;

        String src = renderSource(pkg, simpleName, compactJson);
        JavaFileObject file = filer.createSourceFile(generatedFqn, source);
        try (Writer w = file.openWriter()) {
            w.write(src);
        }
    }

    private String renderSource(String pkg, String simpleName, String json) {
        StringBuilder b = new StringBuilder();
        if (!pkg.isEmpty()) {
            b.append("package ").append(pkg).append(";\n\n");
        }
        b.append("public final class ").append(simpleName).append(" {\n\n");
        b.append("    public static final String JSON = \"")
                .append(JavaStringEscaper.escape(json))
                .append("\";\n\n");
        b.append("    private ").append(simpleName).append("() {}\n");
        b.append("}\n");
        return b.toString();
    }
}
