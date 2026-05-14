package io.github.kurok1.jsonschema.processor;

import io.github.kurok1.jsonschema.core.json.JsonWriter;
import io.github.kurok1.jsonschema.core.model.SchemaDocument;

import javax.annotation.processing.Filer;
import javax.lang.model.element.TypeElement;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.io.Writer;

/**
 * Writes a {@link SchemaDocument} to the annotation processor's class output as
 * a JSON file under {@code META-INF/jsonschema/<fqcn>.json}.
 */
final class SchemaWriter {

    private static final String DEFAULT_DIR = "META-INF/jsonschema";

    private final Filer filer;
    private final boolean pretty;
    private final String outputDir;

    SchemaWriter(Filer filer, boolean pretty, String outputDir) {
        this.filer = filer;
        this.pretty = pretty;
        this.outputDir = (outputDir == null || outputDir.isEmpty()) ? DEFAULT_DIR : outputDir;
    }

    void write(TypeElement source, SchemaDocument document) throws IOException {
        String fqn = source.getQualifiedName().toString();
        String resourcePath = outputDir + "/" + fqn + ".json";
        FileObject file = filer.createResource(
                StandardLocation.CLASS_OUTPUT,
                "",
                resourcePath,
                source);
        JsonWriter jsonWriter = new JsonWriter(pretty);
        String json = jsonWriter.write(document);
        try (Writer w = file.openWriter()) {
            w.write(json);
        }
    }
}
