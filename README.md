# jsonschema-java-parser

A Java annotation processor that generates **JSON Schema (Draft 2020-12)** from your entity classes **at compile time** — no runtime reflection, no class loading, GraalVM-friendly.

Designed to be compatible with **OpenAI Structured Outputs / Function Calling** strict mode.

- Targets **Java 8+** (tested on JDK 8 / 11 / 17 / 21)
- Outputs JSON to `META-INF/jsonschema/<fqcn>.json` (loadable as a classpath resource)
- Honours **Jackson** (`@JsonProperty`, `@JsonIgnore`, `@JsonPropertyDescription`) and **JSR-303 / Jakarta Validation** (`@NotNull`, `@Size`, `@Min`, `@Max`, `@Pattern`, `@Email`, `@Positive`, ...) without depending on either library
- Handles nested types, inheritance, recursion, `Optional<T>`, `Map<String, V>`
- Extensible via `ServiceLoader`-discovered `AnnotationMapper`s

## Quick start

### 1. Configure GitHub Packages access

GitHub Packages requires authentication even for public artifacts. Add a `<server>` entry to your `~/.m2/settings.xml`:

```xml
<settings>
  <servers>
    <server>
      <id>github-kurok1</id>
      <username>YOUR_GITHUB_USERNAME</username>
      <password>YOUR_PERSONAL_ACCESS_TOKEN</password>
    </server>
  </servers>
</settings>
```

The PAT only needs the `read:packages` scope.

### 2. Add the repository and dependencies

```xml
<repositories>
  <repository>
    <id>github-kurok1</id>
    <url>https://maven.pkg.github.com/kurok1/jsonschema-java-parser</url>
  </repository>
</repositories>

<dependencies>
  <dependency>
    <groupId>io.github.kurok1.jsonschema</groupId>
    <artifactId>jsonschema-java-parser-annotations</artifactId>
    <version>0.1.0</version>
  </dependency>
</dependencies>

<build>
  <plugins>
    <plugin>
      <artifactId>maven-compiler-plugin</artifactId>
      <configuration>
        <annotationProcessorPaths>
          <path>
            <groupId>io.github.kurok1.jsonschema</groupId>
            <artifactId>jsonschema-java-parser-processor</artifactId>
            <version>0.1.0</version>
          </path>
        </annotationProcessorPaths>
      </configuration>
    </plugin>
  </plugins>
</build>
```

### 3. Annotate an entity

```java
import io.github.kurok1.jsonschema.annotations.JsonSchema;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

@JsonSchema(title = "User", description = "A registered user.")
public class User {
    @NotNull
    private java.util.UUID id;

    @Size(min = 1, max = 64)
    private String displayName;
}
```

### 4. Compile

```bash
mvn compile
```

Generated artifact: `target/classes/META-INF/jsonschema/com.example.User.json`

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "type": "object",
  "title": "User",
  "description": "A registered user.",
  "properties": {
    "id":          { "type": "string", "format": "uuid" },
    "displayName": { "type": "string", "minLength": 1, "maxLength": 64 }
  },
  "required": ["id"],
  "additionalProperties": false
}
```

Loadable at runtime via:

```java
try (InputStream in = User.class.getClassLoader()
        .getResourceAsStream("META-INF/jsonschema/com.example.User.json")) { ... }
```

## Annotation reference

### `@JsonSchema` — class-level

| Member | Default | Notes |
| --- | --- | --- |
| `id` | `""` | Becomes `$id` in the schema. |
| `title` | `""` | Schema `title`. |
| `description` | `""` | Schema `description`. |
| `draft` | `DRAFT_2020_12` | Only Draft 2020-12 is implemented today. |
| `outputPath` | `""` | Custom output path (relative to `CLASS_OUTPUT`). |
| `additionalProperties` | `false` | Whether the object allows extra fields. |
| `inheritance` | `ALL_OF` | `ALL_OF` (parent goes to `$defs` + `allOf`) or `FLATTEN`. |
| `openaiCompatible` | `false` | See [OpenAI mode](#openai-compatible-mode). |

### `@JsonSchemaProperty` — field-level

| Member | Effect |
| --- | --- |
| `name` | Override property name. |
| `description` | Set `description`. |
| `format` | Override `format`. |
| `required` | Add to `required` list. |
| `defaultValue` | Set `default`. |
| `examples` | Set `examples`. |

### `@JsonSchemaIgnore` — field-level

Skip the field entirely.

### Recognised third-party annotations (auto-detected by FQN)

| Source | Mapped to |
| --- | --- |
| `@JsonProperty(value, required, defaultValue)` | Rename + add to `required` + `default` |
| `@JsonIgnore` | Skip |
| `@JsonPropertyDescription` | `description` |
| `@NotNull` (`javax` / `jakarta`) | `required` |
| `@NotEmpty` | `required` + `minLength: 1` / `minItems: 1` / `minProperties: 1` |
| `@NotBlank` | `required` + `minLength: 1` |
| `@Size(min, max)` | `minLength`/`maxLength`, `minItems`/`maxItems`, or `minProperties`/`maxProperties` |
| `@Min(value)` / `@Max(value)` | `minimum` / `maximum` |
| `@DecimalMin(value, inclusive)` / `@DecimalMax(value, inclusive)` | `minimum`/`exclusiveMinimum` / `maximum`/`exclusiveMaximum` |
| `@Pattern(regexp)` | `pattern` |
| `@Email` | `format: "email"` |
| `@Positive` / `@PositiveOrZero` | `exclusiveMinimum: 0` / `minimum: 0` |
| `@Negative` / `@NegativeOrZero` | `exclusiveMaximum: 0` / `maximum: 0` |
| `@Nonnull`, `@NotNull` (JetBrains / JSR-305 / Lombok / Spring / Checker / JSpecify / FindBugs) | `required` |

## Type mapping

| Java type | JSON Schema |
| --- | --- |
| `String`, `char`, `Character`, `CharSequence` | `{"type": "string"}` |
| `boolean`, `Boolean` | `{"type": "boolean"}` |
| `int`, `long`, `byte`, `short`, `Integer`, `BigInteger`, ... | `{"type": "integer"}` |
| `float`, `double`, `Float`, `Double`, `BigDecimal` | `{"type": "number"}` |
| `Date`, `Instant`, `OffsetDateTime`, `ZonedDateTime`, `LocalDateTime` | `string` + `format: date-time` |
| `LocalDate` | `string` + `format: date` |
| `LocalTime`, `OffsetTime` | `string` + `format: time` |
| `UUID` | `string` + `format: uuid` |
| `URI`, `URL` | `string` + `format: uri` |
| Enum | `{"type": "string", "enum": [...]}` |
| `T[]`, `Collection<T>`, `Iterable<T>`, `List<T>` | `{"type": "array", "items": <T>}` |
| `Set<T>` | as `array` + `uniqueItems: true` |
| `Map<String, V>` | `{"type": "object", "additionalProperties": <V>}` |
| `Optional<T>` | `<T>` (and field is **not** added to `required`) |
| Custom class | `{"$ref": "#/$defs/<FQN>"}` (target goes to `$defs`) |
| Other `java.*` / `javax.*` types | `{"type": "object"}` (no `$defs` entry) |

## Compile-time options

Pass via `-A<key>=<value>` (e.g., `<compilerArgs><arg>-AjsonschemaPretty=false</arg></compilerArgs>`).

| Option | Default | Description |
| --- | --- | --- |
| `jsonschemaPretty` | `true` | Pretty-print generated JSON. |
| `jsonschemaOutputDir` | `META-INF/jsonschema` | Output directory under `CLASS_OUTPUT`. |
| `jsonschemaIncludeJackson` | `true` | Enable Jackson annotation mapping. |
| `jsonschemaIncludeJsr303` | `true` | Enable Bean Validation mapping. |
| `jsonschemaIncludeNullness` | `true` | Enable third-party `@NonNull` recognition. |
| `jsonschemaOpenaiCompatible` | `false` | Globally enable OpenAI strict mode (per-class override via annotation). |
| `jsonschemaGenerateConstants` | `false` | Emit `<pkg>.<Simple>JsonSchema` with `public static final String JSON`. |
| `jsonschemaGenerateRegistry` | `false` | Emit a single registry class with all schemas. |
| `jsonschemaRegistryClass` | `io.github.kurok1.jsonschema.generated.JsonSchemaRegistry` | Custom registry FQN. |

## OpenAI compatible mode

Enable per class:

```java
@JsonSchema(openaiCompatible = true)
public class FunctionArgs { ... }
```

or globally:

```xml
<compilerArgs>
  <arg>-AjsonschemaOpenaiCompatible=true</arg>
</compilerArgs>
```

When enabled, the transformer:

- Forces `additionalProperties: false` on every object
- Adds every property name into `required` (even `Optional<T>` fields)
- Strips keywords OpenAI strict mode does not allow: `minLength`, `maxLength`, `pattern`, `format`, `minimum`, `maximum`, `exclusiveMinimum`, `exclusiveMaximum`, `multipleOf`, `minItems`, `maxItems`, `uniqueItems`, `minProperties`, `maxProperties`, `default`, `examples`, `allOf`, `oneOf`
- Switches inheritance to `FLATTEN` (because `allOf` is disallowed)
- Fails the build if the schema exceeds 100 total properties or 5 levels of nesting (depth counts across `$ref` chains)

## SPI: custom AnnotationMapper

```java
public final class MyMapper implements io.github.kurok1.jsonschema.processor.AnnotationMapper {
    @Override
    public void apply(VariableElement field, FieldContext ctx) {
        if (field.getAnnotation(Sensitive.class) != null) {
            ctx.builder().keyword("x-sensitive", true);
        }
    }
}
```

Register via `META-INF/services/io.github.kurok1.jsonschema.processor.AnnotationMapper` and place the artifact on the annotation processor path.

## Constants / registry generation

```xml
<compilerArgs>
  <arg>-AjsonschemaGenerateConstants=true</arg>
  <arg>-AjsonschemaGenerateRegistry=true</arg>
</compilerArgs>
```

Produces:

```java
package com.example;
public final class UserJsonSchema {
    public static final String JSON = "{...}";
    private UserJsonSchema() {}
}
```

```java
package io.github.kurok1.jsonschema.generated;
public final class JsonSchemaRegistry {
    public static String get(Class<?> type)       { ... }
    public static Set<Class<?>> registered()      { ... }
}
```

## Modules

- `jsonschema-java-parser-annotations` — public annotations, zero dependencies
- `jsonschema-java-parser-core` — schema model, JSON writer, OpenAI transformer
- `jsonschema-java-parser-processor` — APT entry point (consumers add as `annotationProcessorPath`)
- `jsonschema-java-parser-tests` — `compile-testing` integration tests (not deployed)
- `jsonschema-java-parser-examples` — worked example (not deployed)

## Architecture

See [`docs/Architecture.md`](docs/Architecture.md).

## Building locally

```bash
mvn clean install
```

Requires JDK 8 or newer.

## Releasing

The release workflow is triggered by **pushing a `v*` tag** — nothing else to do:

```bash
git tag v0.1.0
git push origin v0.1.0
```

The workflow then:

1. Sets the Maven version to `0.1.0` (strips the leading `v`)
2. Runs `mvn verify` across the reactor
3. Deploys `annotations` / `core` / `processor` to GitHub Packages
4. Auto-creates a GitHub Release for the tag with library jars attached and auto-generated release notes

A manual dispatch (`workflow_dispatch`) with a `version` input does the same thing and creates the matching `v<version>` tag for you.

## License

Apache License 2.0.
