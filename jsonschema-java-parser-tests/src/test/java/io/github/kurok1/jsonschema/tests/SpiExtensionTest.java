package io.github.kurok1.jsonschema.tests;

import io.github.kurok1.jsonschema.processor.JsonSchemaProcessor;
import com.google.testing.compile.Compilation;
import com.google.testing.compile.Compiler;
import com.google.testing.compile.JavaFileObjects;
import org.junit.jupiter.api.Test;

import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

class SpiExtensionTest {

    @Test
    void serviceLoaderMapperRunsWhenEnabled() throws IOException {
        JavaFileObject source = JavaFileObjects.forSourceLines("sample.SpiHost",
                "package sample;",
                "import io.github.kurok1.jsonschema.annotations.JsonSchema;",
                "@JsonSchema",
                "public class SpiHost { String name; }");

        Compilation compilation = Compiler.javac()
                .withOptions("-AjsonschemaPretty=false", "-AspiTestEnabled=true")
                .withProcessors(new JsonSchemaProcessor())
                .compile(source);

        assertThat(compilation).succeeded();
        String content = readGenerated(compilation, "META-INF/jsonschema/sample.SpiHost.json");
        assertThat(content)
                .contains("\"name\":{\"type\":\"string\",\"x-spi-marker\":\"applied\"}");
    }

    @Test
    void serviceLoaderMapperIsInertWhenOptionAbsent() throws IOException {
        JavaFileObject source = JavaFileObjects.forSourceLines("sample.SpiHostInert",
                "package sample;",
                "import io.github.kurok1.jsonschema.annotations.JsonSchema;",
                "@JsonSchema",
                "public class SpiHostInert { String value; }");

        Compilation compilation = Compiler.javac()
                .withOptions("-AjsonschemaPretty=false")
                .withProcessors(new JsonSchemaProcessor())
                .compile(source);

        assertThat(compilation).succeeded();
        String content = readGenerated(compilation, "META-INF/jsonschema/sample.SpiHostInert.json");
        assertThat(content).doesNotContain("x-spi-marker");
    }

    private String readGenerated(Compilation compilation, String path) throws IOException {
        Optional<JavaFileObject> file = compilation.generatedFile(StandardLocation.CLASS_OUTPUT, path);
        if (!file.isPresent()) {
            throw new AssertionError("Expected generated resource " + path + " not found");
        }
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.get().openInputStream(), StandardCharsets.UTF_8))) {
            int ch;
            while ((ch = reader.read()) != -1) {
                sb.append((char) ch);
            }
        }
        return sb.toString();
    }
}
