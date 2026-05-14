package io.github.kurok1.jsonschema.tests;

import io.github.kurok1.jsonschema.processor.JsonSchemaProcessor;
import com.google.testing.compile.Compilation;
import com.google.testing.compile.Compiler;
import com.google.testing.compile.JavaFileObjects;
import org.junit.jupiter.api.Test;

import javax.tools.JavaFileObject;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

class GeneratedClassesTest {

    @Test
    void generatesPerClassConstantsHolder() throws IOException {
        JavaFileObject source = JavaFileObjects.forSourceLines("sample.Profile",
                "package sample;",
                "import io.github.kurok1.jsonschema.annotations.JsonSchema;",
                "@JsonSchema",
                "public class Profile {",
                "    String name;",
                "}");

        Compilation compilation = Compiler.javac()
                .withOptions("-AjsonschemaPretty=false", "-AjsonschemaGenerateConstants=true")
                .withProcessors(new JsonSchemaProcessor())
                .compile(source);

        assertThat(compilation).succeeded();
        Optional<JavaFileObject> generated = compilation.generatedSourceFile("sample.ProfileJsonSchema");
        org.assertj.core.api.Assertions.assertThat(generated).isPresent();
        String content = readContent(generated.get());
        assertThat(content)
                .contains("package sample;")
                .contains("public final class ProfileJsonSchema")
                .contains("public static final String JSON =")
                .contains("\\\"name\\\":{\\\"type\\\":\\\"string\\\"}");
    }

    @Test
    void aggregateRegistryListsAllAnnotatedClasses() throws IOException {
        JavaFileObject userSrc = JavaFileObjects.forSourceLines("sample.User",
                "package sample;",
                "import io.github.kurok1.jsonschema.annotations.JsonSchema;",
                "@JsonSchema",
                "public class User { String name; }");
        JavaFileObject orderSrc = JavaFileObjects.forSourceLines("sample.Order",
                "package sample;",
                "import io.github.kurok1.jsonschema.annotations.JsonSchema;",
                "@JsonSchema",
                "public class Order { String id; }");

        Compilation compilation = Compiler.javac()
                .withOptions("-AjsonschemaPretty=false", "-AjsonschemaGenerateRegistry=true")
                .withProcessors(new JsonSchemaProcessor())
                .compile(userSrc, orderSrc);

        assertThat(compilation).succeeded();
        Optional<JavaFileObject> registry = compilation.generatedSourceFile(
                "io.github.kurok1.jsonschema.generated.JsonSchemaRegistry");
        org.assertj.core.api.Assertions.assertThat(registry).isPresent();
        String content = readContent(registry.get());
        assertThat(content)
                .contains("package io.github.kurok1.jsonschema.generated;")
                .contains("public final class JsonSchemaRegistry")
                .contains("m.put(sample.User.class, \"")
                .contains("m.put(sample.Order.class, \"")
                .contains("public static String get(Class<?> type)")
                .contains("public static Set<Class<?>> registered()");
    }

    @Test
    void customRegistryClassNameIsHonored() throws IOException {
        JavaFileObject source = JavaFileObjects.forSourceLines("sample.Box",
                "package sample;",
                "import io.github.kurok1.jsonschema.annotations.JsonSchema;",
                "@JsonSchema",
                "public class Box { String label; }");

        Compilation compilation = Compiler.javac()
                .withOptions(
                        "-AjsonschemaPretty=false",
                        "-AjsonschemaGenerateRegistry=true",
                        "-AjsonschemaRegistryClass=sample.Schemas")
                .withProcessors(new JsonSchemaProcessor())
                .compile(source);

        assertThat(compilation).succeeded();
        Optional<JavaFileObject> file = compilation.generatedSourceFile("sample.Schemas");
        org.assertj.core.api.Assertions.assertThat(file).isPresent();
        String content = readContent(file.get());
        assertThat(content)
                .contains("package sample;")
                .contains("public final class Schemas");
    }

    @Test
    void doesNotGenerateExtrasWithoutFlags() {
        JavaFileObject source = JavaFileObjects.forSourceLines("sample.Plain",
                "package sample;",
                "import io.github.kurok1.jsonschema.annotations.JsonSchema;",
                "@JsonSchema",
                "public class Plain { int x; }");

        Compilation compilation = Compiler.javac()
                .withOptions("-AjsonschemaPretty=false")
                .withProcessors(new JsonSchemaProcessor())
                .compile(source);

        assertThat(compilation).succeeded();
        org.assertj.core.api.Assertions.assertThat(
                compilation.generatedSourceFile("sample.PlainJsonSchema")).isEmpty();
        org.assertj.core.api.Assertions.assertThat(
                compilation.generatedSourceFile(
                        "io.github.kurok1.jsonschema.generated.JsonSchemaRegistry")).isEmpty();
    }

    private String readContent(JavaFileObject file) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.openInputStream(), StandardCharsets.UTF_8))) {
            int ch;
            while ((ch = reader.read()) != -1) {
                sb.append((char) ch);
            }
        }
        return sb.toString();
    }
}
