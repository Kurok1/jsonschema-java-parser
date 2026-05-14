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

class OpenAiCompatibilityTest {

    @Test
    void forcesAdditionalPropertiesFalseAndAllRequired() throws IOException {
        JavaFileObject source = JavaFileObjects.forSourceLines("sample.Person",
                "package sample;",
                "import io.github.kurok1.jsonschema.annotations.JsonSchema;",
                "@JsonSchema(openaiCompatible = true, additionalProperties = true)",
                "public class Person {",
                "    String name;",
                "    Integer age;",
                "}");

        Compilation compilation = compile(source);

        assertThat(compilation).succeeded();
        String content = readGenerated(compilation, "META-INF/jsonschema/sample.Person.json");
        assertThat(content)
                .contains("\"additionalProperties\":false")
                .contains("\"required\":[\"name\",\"age\"]")
                .doesNotContain("\"additionalProperties\":true");
    }

    @Test
    void stripsUnsupportedValidationKeywords() throws IOException {
        JavaFileObject source = JavaFileObjects.forSourceLines("sample.WithConstraints",
                "package sample;",
                "import io.github.kurok1.jsonschema.annotations.JsonSchema;",
                "import javax.validation.constraints.Size;",
                "import javax.validation.constraints.Min;",
                "import javax.validation.constraints.Pattern;",
                "import javax.validation.constraints.Email;",
                "@JsonSchema(openaiCompatible = true)",
                "public class WithConstraints {",
                "    @Size(min = 2, max = 32) String name;",
                "    @Min(0) int age;",
                "    @Pattern(regexp = \"\\\\d+\") String zip;",
                "    @Email String contact;",
                "}");

        Compilation compilation = compile(source);

        assertThat(compilation).succeeded();
        String content = readGenerated(compilation, "META-INF/jsonschema/sample.WithConstraints.json");
        assertThat(content)
                .doesNotContain("\"minLength\"")
                .doesNotContain("\"maxLength\"")
                .doesNotContain("\"pattern\"")
                .doesNotContain("\"format\"")
                .doesNotContain("\"minimum\"")
                .contains("\"required\":[\"name\",\"age\",\"zip\",\"contact\"]");
    }

    @Test
    void inheritanceFlattensInOpenAiMode() throws IOException {
        JavaFileObject parent = JavaFileObjects.forSourceLines("sample.Animal",
                "package sample;",
                "public class Animal { String species; }");
        JavaFileObject child = JavaFileObjects.forSourceLines("sample.Cat",
                "package sample;",
                "import io.github.kurok1.jsonschema.annotations.JsonSchema;",
                "@JsonSchema(openaiCompatible = true)",
                "public class Cat extends Animal { String color; }");

        Compilation compilation = compile(parent, child);

        assertThat(compilation).succeeded();
        String content = readGenerated(compilation, "META-INF/jsonschema/sample.Cat.json");
        assertThat(content)
                .doesNotContain("\"allOf\"")
                .doesNotContain("\"$defs\"")
                .contains("\"properties\":{\"species\":{\"type\":\"string\"},\"color\":{\"type\":\"string\"}}")
                .contains("\"required\":[\"species\",\"color\"]");
    }

    @Test
    void depthLimitFailsCompilation() {
        JavaFileObject l5 = JavaFileObjects.forSourceLines("sample.L5",
                "package sample;",
                "public class L5 { String value; }");
        JavaFileObject l4 = JavaFileObjects.forSourceLines("sample.L4",
                "package sample;",
                "public class L4 { L5 next; }");
        JavaFileObject l3 = JavaFileObjects.forSourceLines("sample.L3",
                "package sample;",
                "public class L3 { L4 next; }");
        JavaFileObject l2 = JavaFileObjects.forSourceLines("sample.L2",
                "package sample;",
                "public class L2 { L3 next; }");
        JavaFileObject l1 = JavaFileObjects.forSourceLines("sample.L1",
                "package sample;",
                "public class L1 { L2 next; }");
        JavaFileObject root = JavaFileObjects.forSourceLines("sample.DeepRoot",
                "package sample;",
                "import io.github.kurok1.jsonschema.annotations.JsonSchema;",
                "@JsonSchema(openaiCompatible = true)",
                "public class DeepRoot { L1 chain; }");

        Compilation compilation = compile(root, l1, l2, l3, l4, l5);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining("nesting depth");
    }

    @Test
    void propertyCountLimitFailsCompilation() {
        StringBuilder body = new StringBuilder();
        for (int i = 0; i < 101; i++) {
            body.append("    String f").append(i).append(";\n");
        }
        JavaFileObject source = JavaFileObjects.forSourceLines("sample.WideRoot",
                "package sample;",
                "import io.github.kurok1.jsonschema.annotations.JsonSchema;",
                "@JsonSchema(openaiCompatible = true)",
                "public class WideRoot {",
                body.toString(),
                "}");

        Compilation compilation = compile(source);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining("100 total properties");
    }

    @Test
    void globalFlagAlsoActivates() throws IOException {
        JavaFileObject source = JavaFileObjects.forSourceLines("sample.GlobalFlag",
                "package sample;",
                "import io.github.kurok1.jsonschema.annotations.JsonSchema;",
                "@JsonSchema",
                "public class GlobalFlag {",
                "    String label;",
                "    Integer count;",
                "}");

        Compilation compilation = Compiler.javac()
                .withOptions(
                        "-AjsonschemaPretty=false",
                        "-AjsonschemaOpenaiCompatible=true")
                .withProcessors(new JsonSchemaProcessor())
                .compile(source);

        assertThat(compilation).succeeded();
        String content = readGenerated(compilation, "META-INF/jsonschema/sample.GlobalFlag.json");
        assertThat(content)
                .contains("\"required\":[\"label\",\"count\"]")
                .contains("\"additionalProperties\":false");
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
