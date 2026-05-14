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

class SchemaRefAndInheritanceTest {

    @Test
    void emitsRefForNestedCustomType() throws IOException {
        JavaFileObject customer = JavaFileObjects.forSourceLines("sample.Customer",
                "package sample;",
                "public class Customer {",
                "    String name;",
                "    String email;",
                "}");
        JavaFileObject order = JavaFileObjects.forSourceLines("sample.Order",
                "package sample;",
                "import io.github.kurok1.jsonschema.annotations.JsonSchema;",
                "@JsonSchema",
                "public class Order {",
                "    String id;",
                "    Customer customer;",
                "}");

        Compilation compilation = compile(customer, order);

        assertThat(compilation).succeeded();
        String content = readGenerated(compilation, "META-INF/jsonschema/sample.Order.json");
        assertThat(content)
                .contains("\"customer\":{\"$ref\":\"#/$defs/sample.Customer\"}")
                .contains("\"$defs\":{")
                .contains("\"sample.Customer\":{\"type\":\"object\"")
                .contains("\"name\":{\"type\":\"string\"}")
                .contains("\"email\":{\"type\":\"string\"}");
    }

    @Test
    void handlesSelfReferenceWithoutInfiniteLoop() throws IOException {
        JavaFileObject node = JavaFileObjects.forSourceLines("sample.Node",
                "package sample;",
                "import io.github.kurok1.jsonschema.annotations.JsonSchema;",
                "import java.util.List;",
                "@JsonSchema",
                "public class Node {",
                "    String label;",
                "    Node parent;",
                "    List<Node> children;",
                "}");

        Compilation compilation = compile(node);

        assertThat(compilation).succeeded();
        String content = readGenerated(compilation, "META-INF/jsonschema/sample.Node.json");
        assertThat(content)
                .contains("\"parent\":{\"$ref\":\"#/$defs/sample.Node\"}")
                .contains("\"children\":{\"type\":\"array\",\"items\":{\"$ref\":\"#/$defs/sample.Node\"}}")
                .contains("\"$defs\":{\"sample.Node\":{\"type\":\"object\"");
    }

    @Test
    void handlesMutualRecursion() throws IOException {
        JavaFileObject a = JavaFileObjects.forSourceLines("sample.A",
                "package sample;",
                "import io.github.kurok1.jsonschema.annotations.JsonSchema;",
                "@JsonSchema",
                "public class A {",
                "    B b;",
                "}");
        JavaFileObject b = JavaFileObjects.forSourceLines("sample.B",
                "package sample;",
                "public class B {",
                "    A a;",
                "}");

        Compilation compilation = compile(a, b);

        assertThat(compilation).succeeded();
        String content = readGenerated(compilation, "META-INF/jsonschema/sample.A.json");
        assertThat(content)
                .contains("\"b\":{\"$ref\":\"#/$defs/sample.B\"}")
                .contains("\"sample.B\":{\"type\":\"object\",\"properties\":{\"a\":{\"$ref\":\"#/$defs/sample.A\"}}")
                .contains("\"sample.A\":{\"type\":\"object\",\"properties\":{\"b\":{\"$ref\":\"#/$defs/sample.B\"}}");
    }

    @Test
    void emitsAllOfForInheritanceByDefault() throws IOException {
        JavaFileObject animal = JavaFileObjects.forSourceLines("sample.Animal",
                "package sample;",
                "public class Animal {",
                "    String species;",
                "}");
        JavaFileObject cat = JavaFileObjects.forSourceLines("sample.Cat",
                "package sample;",
                "import io.github.kurok1.jsonschema.annotations.JsonSchema;",
                "@JsonSchema",
                "public class Cat extends Animal {",
                "    String color;",
                "}");

        Compilation compilation = compile(animal, cat);

        assertThat(compilation).succeeded();
        String content = readGenerated(compilation, "META-INF/jsonschema/sample.Cat.json");
        assertThat(content)
                .contains("\"allOf\":["
                        + "{\"$ref\":\"#/$defs/sample.Animal\"},"
                        + "{\"properties\":{\"color\":{\"type\":\"string\"}}}"
                        + "]")
                .contains("\"$defs\":{\"sample.Animal\":{"
                        + "\"type\":\"object\",\"properties\":{\"species\":{\"type\":\"string\"}}"
                        + ",\"additionalProperties\":false}}");
    }

    @Test
    void flattensFieldsWhenStrategyIsFlatten() throws IOException {
        JavaFileObject livingThing = JavaFileObjects.forSourceLines("sample.LivingThing",
                "package sample;",
                "public class LivingThing {",
                "    long birthYear;",
                "}");
        JavaFileObject animal = JavaFileObjects.forSourceLines("sample.Animal",
                "package sample;",
                "public class Animal extends LivingThing {",
                "    String species;",
                "}");
        JavaFileObject cat = JavaFileObjects.forSourceLines("sample.Cat",
                "package sample;",
                "import io.github.kurok1.jsonschema.annotations.JsonSchema;",
                "import io.github.kurok1.jsonschema.annotations.InheritanceStrategy;",
                "@JsonSchema(inheritance = InheritanceStrategy.FLATTEN)",
                "public class Cat extends Animal {",
                "    String color;",
                "}");

        Compilation compilation = compile(livingThing, animal, cat);

        assertThat(compilation).succeeded();
        String content = readGenerated(compilation, "META-INF/jsonschema/sample.Cat.json");
        assertThat(content)
                .contains("\"properties\":{"
                        + "\"birthYear\":{\"type\":\"integer\"}"
                        + ",\"species\":{\"type\":\"string\"}"
                        + ",\"color\":{\"type\":\"string\"}"
                        + "}")
                .doesNotContain("\"allOf\"")
                .doesNotContain("\"$defs\"");
    }

    @Test
    void doesNotRegisterStdlibTypes() throws IOException {
        JavaFileObject source = JavaFileObjects.forSourceLines("sample.Holder",
                "package sample;",
                "import io.github.kurok1.jsonschema.annotations.JsonSchema;",
                "import java.io.File;",
                "@JsonSchema",
                "public class Holder {",
                "    File file;",
                "}");

        Compilation compilation = compile(source);

        assertThat(compilation).succeeded();
        String content = readGenerated(compilation, "META-INF/jsonschema/sample.Holder.json");
        assertThat(content)
                .contains("\"file\":{\"type\":\"object\"}")
                .doesNotContain("\"$defs\"")
                .doesNotContain("$ref");
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
