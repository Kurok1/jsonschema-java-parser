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

class SchemaGenerationTest {

    @Test
    void generatesScalarSchema() throws IOException {
        JavaFileObject source = JavaFileObjects.forSourceLines("sample.User",
                "package sample;",
                "import io.github.kurok1.jsonschema.annotations.JsonSchema;",
                "@JsonSchema(title = \"User\")",
                "public class User {",
                "    String name;",
                "    int age;",
                "    boolean active;",
                "}");

        Compilation compilation = compile(source);

        assertThat(compilation).succeeded();
        String content = readGenerated(compilation, "META-INF/jsonschema/sample.User.json");
        assertThat(content).isEqualTo("{\"$schema\":\"https://json-schema.org/draft/2020-12/schema\""
                + ",\"type\":\"object\""
                + ",\"title\":\"User\""
                + ",\"properties\":{"
                + "\"name\":{\"type\":\"string\"}"
                + ",\"age\":{\"type\":\"integer\"}"
                + ",\"active\":{\"type\":\"boolean\"}"
                + "}"
                + ",\"additionalProperties\":false}");
    }

    @Test
    void mapsCollectionsAndArraysAndMap() throws IOException {
        JavaFileObject source = JavaFileObjects.forSourceLines("sample.Bag",
                "package sample;",
                "import io.github.kurok1.jsonschema.annotations.JsonSchema;",
                "import java.util.List;",
                "import java.util.Set;",
                "import java.util.Map;",
                "@JsonSchema",
                "public class Bag {",
                "    List<String> tags;",
                "    Set<Integer> ids;",
                "    String[] aliases;",
                "    Map<String, Long> counters;",
                "}");

        Compilation compilation = compile(source);

        assertThat(compilation).succeeded();
        String content = readGenerated(compilation, "META-INF/jsonschema/sample.Bag.json");
        assertThat(content)
                .contains("\"tags\":{\"type\":\"array\",\"items\":{\"type\":\"string\"}}")
                .contains("\"ids\":{\"type\":\"array\",\"items\":{\"type\":\"integer\"},\"uniqueItems\":true}")
                .contains("\"aliases\":{\"type\":\"array\",\"items\":{\"type\":\"string\"}}")
                .contains("\"counters\":{\"type\":\"object\",\"additionalProperties\":{\"type\":\"integer\"}}");
    }

    @Test
    void mapsEnumAndDateTimeAndUuid() throws IOException {
        JavaFileObject status = JavaFileObjects.forSourceLines("sample.Status",
                "package sample;",
                "public enum Status { OPEN, CLOSED }");
        JavaFileObject source = JavaFileObjects.forSourceLines("sample.Order",
                "package sample;",
                "import io.github.kurok1.jsonschema.annotations.JsonSchema;",
                "import java.time.Instant;",
                "import java.time.LocalDate;",
                "import java.util.UUID;",
                "@JsonSchema",
                "public class Order {",
                "    UUID id;",
                "    Instant createdAt;",
                "    LocalDate dueDate;",
                "    Status status;",
                "}");

        Compilation compilation = compile(status, source);

        assertThat(compilation).succeeded();
        String content = readGenerated(compilation, "META-INF/jsonschema/sample.Order.json");
        assertThat(content)
                .contains("\"id\":{\"type\":\"string\",\"format\":\"uuid\"}")
                .contains("\"createdAt\":{\"type\":\"string\",\"format\":\"date-time\"}")
                .contains("\"dueDate\":{\"type\":\"string\",\"format\":\"date\"}")
                .contains("\"status\":{\"type\":\"string\",\"enum\":[\"OPEN\",\"CLOSED\"]}");
    }

    @Test
    void honorsIgnoreAndPropertyOverrides() throws IOException {
        JavaFileObject source = JavaFileObjects.forSourceLines("sample.Profile",
                "package sample;",
                "import io.github.kurok1.jsonschema.annotations.JsonSchema;",
                "import io.github.kurok1.jsonschema.annotations.JsonSchemaIgnore;",
                "import io.github.kurok1.jsonschema.annotations.JsonSchemaProperty;",
                "@JsonSchema",
                "public class Profile {",
                "    @JsonSchemaProperty(name = \"display_name\", required = true,",
                "                        description = \"shown in UI\")",
                "    String displayName;",
                "    @JsonSchemaIgnore",
                "    String passwordHash;",
                "    Integer score;",
                "}");

        Compilation compilation = compile(source);

        assertThat(compilation).succeeded();
        String content = readGenerated(compilation, "META-INF/jsonschema/sample.Profile.json");
        assertThat(content)
                .contains("\"display_name\":{\"type\":\"string\",\"description\":\"shown in UI\"}")
                .contains("\"score\":{\"type\":\"integer\"}")
                .doesNotContain("passwordHash")
                .contains("\"required\":[\"display_name\"]");
    }

    @Test
    void prettyPrintIsDefault() throws IOException {
        JavaFileObject source = JavaFileObjects.forSourceLines("sample.Tiny",
                "package sample;",
                "import io.github.kurok1.jsonschema.annotations.JsonSchema;",
                "@JsonSchema",
                "public class Tiny { String x; }");

        Compilation compilation = Compiler.javac()
                .withProcessors(new JsonSchemaProcessor())
                .compile(source);

        assertThat(compilation).succeeded();
        String content = readGenerated(compilation, "META-INF/jsonschema/sample.Tiny.json");
        assertThat(content)
                .contains("\n  \"type\": \"object\",")
                .contains("\n    \"x\":");
    }

    private Compilation compile(JavaFileObject... sources) {
        return Compiler.javac()
                .withOptions("-AjsonschemaPretty=false")
                .withProcessors(new JsonSchemaProcessor())
                .compile(sources);
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
