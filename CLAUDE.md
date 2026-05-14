# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & test

Reactor commands (run from repo root):

```bash
mvn clean install              # full build + install all modules to ~/.m2
mvn -pl jsonschema-java-parser-tests test           # run APT integration tests only
mvn -pl jsonschema-java-parser-tests test -Dtest=OpenAiCompatibilityTest        # single test class
mvn -pl jsonschema-java-parser-tests test -Dtest=OpenAiCompatibilityTest#methodName   # single test method
mvn -pl jsonschema-java-parser-examples compile     # regenerate example schemas under target/classes/META-INF/jsonschema/
```

**Critical workflow caveat**: the `tests` module exercises the processor via `google compile-testing`, which loads the processor jar from the local Maven repo. After editing anything in `jsonschema-java-parser-processor` (or modules it depends on), you **must** run `mvn install` before re-running tests, or compile-testing will silently run the stale processor. `mvn install -pl jsonschema-java-parser-processor -am` is the minimal fix.

Java toolchain: source/target is 1.8. CI runs against JDK 8/11/17/21 (matrix in `.github/workflows/ci.yml`); locally any of those works.

## Architecture

Five-module Maven reactor; **dependency direction is one-way** to keep the annotation jar dependency-free:

```
annotations  ◄── core ◄── processor ◄── tests
                              ▲
                              └── examples (consumer demo)
```

- `jsonschema-java-parser-annotations` — public API (`@JsonSchema`, `@JsonSchemaProperty`, `@JsonSchemaIgnore`, `Draft`, `InheritanceStrategy`). Zero deps. This jar ships to consumers as a regular compile dep.
- `jsonschema-java-parser-core` — schema IR (`model.SchemaNode`, immutable LinkedHashMap-backed builder), JSON serializer (`json.JsonWriter`, hand-rolled no-deps), and the OpenAI strict-mode rewriter (`openai.OpenAiCompatibilityTransformer`). No `javax.lang.model` references here — keep it pure data so it stays testable without a compiler env.
- `jsonschema-java-parser-processor` — the actual `AbstractProcessor`. Ships to consumers as an `<annotationProcessorPath>`, NOT a runtime dep.
- `jsonschema-java-parser-tests` — `compile-testing` harness. Not deployed (`maven.deploy.skip=true`).
- `jsonschema-java-parser-examples` — worked example. Not deployed.

### Processor pipeline (`JsonSchemaProcessor.process`)

For each `@JsonSchema`-annotated `TypeElement`:

1. `SchemaBuilder.build(typeElement)` walks fields via `javax.lang.model` (no reflection, no class loading — that's the whole point).
2. Per-field flow: `TypeResolver.resolve(TypeMirror, TypeRegistry)` produces a `SchemaNode` for the type; then every registered `AnnotationMapper` runs `apply(field, FieldContext)` to mutate the builder (rename, mark required, add format, etc.).
3. `TypeResolver` distinguishes three buckets: (a) scalar/format-mapped JDK types → inline schema; (b) other `java.*`/`javax.*` types → inline `{type:object}` with **no** `$defs` entry; (c) user types → `$ref` + register in `TypeRegistry` for inclusion in `$defs`. The asymmetry is intentional — we don't want to recurse into JDK types we don't model.
4. If class- or global-level `openaiCompatible` is on, `OpenAiCompatibilityTransformer.transform` rewrites the whole schema in place.
5. `SchemaWriter` serializes via `JsonWriter` and writes to `META-INF/jsonschema/<fqcn>.json` under `StandardLocation.CLASS_OUTPUT`.
6. Optional outputs: `ConstantsClassGenerator` emits a per-class `<Simple>JsonSchema` with a `JSON` string constant; `SchemaRegistryGenerator` aggregates all generated schemas into a single registry class, emitted at `processingOver()`.

### AnnotationMapper SPI

Built-in mappers live in the `processor.mapper` subpackage (`JsonSchemaSelfMapper`, `JacksonMapper`, `ValidationMapper`, `NullnessMapper`) and are kept package-private; the only public entry point is the `BuiltinMappers.create(...)` factory that `JsonSchemaProcessor` calls. They're toggled by `-AjsonschemaInclude{Jackson,Jsr303,Nullness}`. **Third-party mappers** are loaded via `ServiceLoader<AnnotationMapper>` from the annotation processor classpath and require a no-arg constructor; they receive `ProcessingEnvironment` through `default init(...)`. Built-in mappers detect external annotations by FQN string match (via `Annotations.find` / `present`), so Jackson and Bean Validation are not compile-time deps of the processor.

`ValidationMapper` covers both `javax.validation.*` and `jakarta.validation.*` via `pair()` helper — every check tries both FQNs. `Size` is type-conditional (string→`minLength`, array→`minItems`, object→`minProperties`).

### OpenAI strict-mode invariants

`OpenAiCompatibilityTransformer` enforces a hard subset:
- Strips 18 keywords (`minLength`/`maxLength`/`pattern`/`format`/`minimum`/`maximum`/`exclusive*`/`multipleOf`/`min*Items`/`uniqueItems`/`min*Properties`/`default`/`examples`/`allOf`/`oneOf`).
- Forces `additionalProperties: false` and adds **every** property name into `required` (even `Optional<T>` fields — strict mode requires this).
- Forces `InheritanceStrategy.FLATTEN` (since `allOf` is disallowed).
- Validates ≤100 total properties and ≤5 nesting levels; depth tracking follows `$ref` chains using a visited set, so an indirected schema can't sneak past the limit.

### Inheritance

`InheritanceStrategy.ALL_OF` is the default: parent class becomes its own `$defs` entry and child schema references it via `allOf`. `FLATTEN` inlines parent fields directly. OpenAI mode forces `FLATTEN`.

## Conventions for editing this codebase

- The processor MUST run on JDK 8 — no `var`, no `Map.of`, no records, no `instanceof` patterns. Test these locally via JDK 8 if you change processor code; CI catches it but the round-trip is slow.
- Don't add a runtime dependency on Jackson or `javax.validation` anywhere — the entire point is FQN-string detection. If you find yourself wanting `import com.fasterxml.jackson...` outside test code, stop.
- `core` module must remain free of `javax.lang.model.*` imports. That separation lets us unit-test schema transforms without spinning up a compiler.
- When adding fields/keywords to `SchemaNode`, also update `OpenAiCompatibilityTransformer.STRIPPED_KEYWORDS` if the new keyword isn't in OpenAI's strict subset — silent drift here is a real bug.
- `compile-testing` quirk: `messager.printMessage(ERROR, msg, element)` with an element argument can be **silently swallowed** by the test harness, making failures look like "no files were generated." If reporting an error from the processor, pass `msg` only — no element arg. (`JsonSchemaProcessor`'s catch blocks already follow this rule; don't regress.)

## Release flow

Pushing a `v*` tag triggers `.github/workflows/publish.yml`, which sets the Maven version (strips leading `v`), runs `mvn verify`, deploys `annotations`/`core`/`processor` to GitHub Packages (`maven.pkg.github.com/kurok1/jsonschema-java-parser`), and auto-creates a GitHub Release with the jars attached. `workflow_dispatch` with a `version` input does the same and creates the tag for you. Tests and examples modules are intentionally excluded from deploy.

Consumers of GitHub Packages need a PAT with `read:packages` in their `~/.m2/settings.xml` under server id `github-kurok1` — this is documented in `README.md`.
