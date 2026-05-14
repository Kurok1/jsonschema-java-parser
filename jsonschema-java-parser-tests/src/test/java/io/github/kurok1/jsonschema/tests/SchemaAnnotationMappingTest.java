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

class SchemaAnnotationMappingTest {

    @Test
    void jacksonJsonPropertyRenamesAndRequires() throws IOException {
        JavaFileObject source = JavaFileObjects.forSourceLines("sample.JacksonRename",
                "package sample;",
                "import io.github.kurok1.jsonschema.annotations.JsonSchema;",
                "import com.fasterxml.jackson.annotation.JsonProperty;",
                "@JsonSchema",
                "public class JacksonRename {",
                "    @JsonProperty(value = \"display_name\", required = true)",
                "    String displayName;",
                "}");

        Compilation compilation = compile(source);

        assertThat(compilation).succeeded();
        String content = readGenerated(compilation, "META-INF/jsonschema/sample.JacksonRename.json");
        assertThat(content)
                .contains("\"properties\":{\"display_name\":{\"type\":\"string\"}}")
                .contains("\"required\":[\"display_name\"]");
    }

    @Test
    void jacksonJsonIgnoreSkipsField() throws IOException {
        JavaFileObject source = JavaFileObjects.forSourceLines("sample.JacksonIgnore",
                "package sample;",
                "import io.github.kurok1.jsonschema.annotations.JsonSchema;",
                "import com.fasterxml.jackson.annotation.JsonIgnore;",
                "@JsonSchema",
                "public class JacksonIgnore {",
                "    String visible;",
                "    @JsonIgnore",
                "    String secret;",
                "}");

        Compilation compilation = compile(source);

        assertThat(compilation).succeeded();
        String content = readGenerated(compilation, "META-INF/jsonschema/sample.JacksonIgnore.json");
        assertThat(content)
                .contains("\"visible\":{\"type\":\"string\"}")
                .doesNotContain("secret");
    }

    @Test
    void jacksonJsonPropertyDescriptionSetsDescription() throws IOException {
        JavaFileObject source = JavaFileObjects.forSourceLines("sample.JacksonDesc",
                "package sample;",
                "import io.github.kurok1.jsonschema.annotations.JsonSchema;",
                "import com.fasterxml.jackson.annotation.JsonPropertyDescription;",
                "@JsonSchema",
                "public class JacksonDesc {",
                "    @JsonPropertyDescription(\"the user identifier\")",
                "    String id;",
                "}");

        Compilation compilation = compile(source);

        assertThat(compilation).succeeded();
        String content = readGenerated(compilation, "META-INF/jsonschema/sample.JacksonDesc.json");
        assertThat(content)
                .contains("\"id\":{\"type\":\"string\",\"description\":\"the user identifier\"}");
    }

    @Test
    void jsr303NotNullImpliesRequired() throws IOException {
        JavaFileObject source = JavaFileObjects.forSourceLines("sample.NotNullDemo",
                "package sample;",
                "import io.github.kurok1.jsonschema.annotations.JsonSchema;",
                "import javax.validation.constraints.NotNull;",
                "@JsonSchema",
                "public class NotNullDemo {",
                "    @NotNull",
                "    String name;",
                "    String optional;",
                "}");

        Compilation compilation = compile(source);

        assertThat(compilation).succeeded();
        String content = readGenerated(compilation, "META-INF/jsonschema/sample.NotNullDemo.json");
        assertThat(content)
                .contains("\"required\":[\"name\"]");
    }

    @Test
    void jsr303SizeOnStringMapsToLengthBounds() throws IOException {
        JavaFileObject source = JavaFileObjects.forSourceLines("sample.SizeString",
                "package sample;",
                "import io.github.kurok1.jsonschema.annotations.JsonSchema;",
                "import javax.validation.constraints.Size;",
                "@JsonSchema",
                "public class SizeString {",
                "    @Size(min = 2, max = 32)",
                "    String username;",
                "}");

        Compilation compilation = compile(source);

        assertThat(compilation).succeeded();
        String content = readGenerated(compilation, "META-INF/jsonschema/sample.SizeString.json");
        assertThat(content)
                .contains("\"username\":{\"type\":\"string\",\"minLength\":2,\"maxLength\":32}");
    }

    @Test
    void jsr303SizeOnArrayMapsToItemsBounds() throws IOException {
        JavaFileObject source = JavaFileObjects.forSourceLines("sample.SizeArray",
                "package sample;",
                "import io.github.kurok1.jsonschema.annotations.JsonSchema;",
                "import javax.validation.constraints.Size;",
                "import java.util.List;",
                "@JsonSchema",
                "public class SizeArray {",
                "    @Size(min = 1, max = 5)",
                "    List<String> tags;",
                "}");

        Compilation compilation = compile(source);

        assertThat(compilation).succeeded();
        String content = readGenerated(compilation, "META-INF/jsonschema/sample.SizeArray.json");
        assertThat(content)
                .contains("\"tags\":{\"type\":\"array\",\"items\":{\"type\":\"string\"},\"minItems\":1,\"maxItems\":5}");
    }

    @Test
    void jsr303MinMaxMapsToNumericBounds() throws IOException {
        JavaFileObject source = JavaFileObjects.forSourceLines("sample.MinMaxDemo",
                "package sample;",
                "import io.github.kurok1.jsonschema.annotations.JsonSchema;",
                "import javax.validation.constraints.Min;",
                "import javax.validation.constraints.Max;",
                "@JsonSchema",
                "public class MinMaxDemo {",
                "    @Min(0) @Max(100)",
                "    int score;",
                "}");

        Compilation compilation = compile(source);

        assertThat(compilation).succeeded();
        String content = readGenerated(compilation, "META-INF/jsonschema/sample.MinMaxDemo.json");
        assertThat(content)
                .contains("\"score\":{\"type\":\"integer\",\"minimum\":0,\"maximum\":100}");
    }

    @Test
    void jsr303PatternAndEmail() throws IOException {
        JavaFileObject source = JavaFileObjects.forSourceLines("sample.PatternDemo",
                "package sample;",
                "import io.github.kurok1.jsonschema.annotations.JsonSchema;",
                "import javax.validation.constraints.Pattern;",
                "import javax.validation.constraints.Email;",
                "@JsonSchema",
                "public class PatternDemo {",
                "    @Pattern(regexp = \"^\\\\d{6}$\")",
                "    String zip;",
                "    @Email",
                "    String contact;",
                "}");

        Compilation compilation = compile(source);

        assertThat(compilation).succeeded();
        String content = readGenerated(compilation, "META-INF/jsonschema/sample.PatternDemo.json");
        assertThat(content)
                .contains("\"zip\":{\"type\":\"string\",\"pattern\":\"^\\\\d{6}$\"}")
                .contains("\"contact\":{\"type\":\"string\",\"format\":\"email\"}");
    }

    @Test
    void jsr303PositiveAndNotBlank() throws IOException {
        JavaFileObject source = JavaFileObjects.forSourceLines("sample.GuardDemo",
                "package sample;",
                "import io.github.kurok1.jsonschema.annotations.JsonSchema;",
                "import javax.validation.constraints.Positive;",
                "import javax.validation.constraints.NotBlank;",
                "@JsonSchema",
                "public class GuardDemo {",
                "    @Positive",
                "    long quantity;",
                "    @NotBlank",
                "    String label;",
                "}");

        Compilation compilation = compile(source);

        assertThat(compilation).succeeded();
        String content = readGenerated(compilation, "META-INF/jsonschema/sample.GuardDemo.json");
        assertThat(content)
                .contains("\"quantity\":{\"type\":\"integer\",\"exclusiveMinimum\":0}")
                .contains("\"label\":{\"type\":\"string\",\"minLength\":1}")
                .contains("\"required\":[\"label\"]");
    }

    @Test
    void optionalUnwrapsTypeAndDropsRequired() throws IOException {
        JavaFileObject source = JavaFileObjects.forSourceLines("sample.OptionalDemo",
                "package sample;",
                "import io.github.kurok1.jsonschema.annotations.JsonSchema;",
                "import javax.validation.constraints.NotNull;",
                "import java.util.Optional;",
                "@JsonSchema",
                "public class OptionalDemo {",
                "    @NotNull",
                "    Optional<String> nickname;",
                "}");

        Compilation compilation = compile(source);

        assertThat(compilation).succeeded();
        String content = readGenerated(compilation, "META-INF/jsonschema/sample.OptionalDemo.json");
        assertThat(content)
                .contains("\"nickname\":{\"type\":\"string\"}")
                .doesNotContain("\"required\"");
    }

    @Test
    void disablingJsr303SuppressesConstraints() throws IOException {
        JavaFileObject source = JavaFileObjects.forSourceLines("sample.DisabledDemo",
                "package sample;",
                "import io.github.kurok1.jsonschema.annotations.JsonSchema;",
                "import javax.validation.constraints.NotNull;",
                "import javax.validation.constraints.Size;",
                "@JsonSchema",
                "public class DisabledDemo {",
                "    @NotNull @Size(min = 1, max = 8)",
                "    String token;",
                "}");

        Compilation compilation = Compiler.javac()
                .withOptions("-AjsonschemaPretty=false", "-AjsonschemaIncludeJsr303=false")
                .withProcessors(new JsonSchemaProcessor())
                .compile(source);

        assertThat(compilation).succeeded();
        String content = readGenerated(compilation, "META-INF/jsonschema/sample.DisabledDemo.json");
        assertThat(content)
                .contains("\"token\":{\"type\":\"string\"}")
                .doesNotContain("\"minLength\"")
                .doesNotContain("\"required\"");
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
