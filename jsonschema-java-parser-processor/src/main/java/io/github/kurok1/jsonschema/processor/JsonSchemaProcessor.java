package io.github.kurok1.jsonschema.processor;

import io.github.kurok1.jsonschema.annotations.JsonSchema;
import io.github.kurok1.jsonschema.core.json.JsonWriter;
import io.github.kurok1.jsonschema.core.model.SchemaDocument;
import io.github.kurok1.jsonschema.processor.mapper.BuiltinMappers;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.Set;

@SupportedAnnotationTypes("io.github.kurok1.jsonschema.annotations.JsonSchema")
@SupportedOptions({
        JsonSchemaProcessor.OPT_PRETTY,
        JsonSchemaProcessor.OPT_OUTPUT_DIR,
        JsonSchemaProcessor.OPT_INCLUDE_JACKSON,
        JsonSchemaProcessor.OPT_INCLUDE_JSR303,
        JsonSchemaProcessor.OPT_INCLUDE_NULLNESS,
        JsonSchemaProcessor.OPT_OPENAI_COMPATIBLE,
        JsonSchemaProcessor.OPT_GENERATE_CONSTANTS,
        JsonSchemaProcessor.OPT_GENERATE_REGISTRY,
        JsonSchemaProcessor.OPT_REGISTRY_CLASS
})
public final class JsonSchemaProcessor extends AbstractProcessor {

    private static final String DEFAULT_REGISTRY_FQN =
            "io.github.kurok1.jsonschema.generated.JsonSchemaRegistry";

    static final String OPT_PRETTY = "jsonschemaPretty";
    static final String OPT_OUTPUT_DIR = "jsonschemaOutputDir";
    static final String OPT_INCLUDE_JACKSON = "jsonschemaIncludeJackson";
    static final String OPT_INCLUDE_JSR303 = "jsonschemaIncludeJsr303";
    static final String OPT_INCLUDE_NULLNESS = "jsonschemaIncludeNullness";
    static final String OPT_OPENAI_COMPATIBLE = "jsonschemaOpenaiCompatible";
    static final String OPT_GENERATE_CONSTANTS = "jsonschemaGenerateConstants";
    static final String OPT_GENERATE_REGISTRY = "jsonschemaGenerateRegistry";
    static final String OPT_REGISTRY_CLASS = "jsonschemaRegistryClass";

    private TypeResolver typeResolver;
    private SchemaBuilder schemaBuilder;
    private SchemaWriter schemaWriter;
    private ConstantsClassGenerator constantsGenerator;
    private SchemaRegistryGenerator registryGenerator;
    private Messager messager;

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public synchronized void init(ProcessingEnvironment env) {
        super.init(env);
        this.messager = env.getMessager();
        this.typeResolver = new TypeResolver(env);
        boolean openai = parseBoolean(env.getOptions().get(OPT_OPENAI_COMPATIBLE), false);
        this.schemaBuilder = new SchemaBuilder(typeResolver, buildMappers(env), openai);

        boolean pretty = parseBoolean(env.getOptions().get(OPT_PRETTY), true);
        String outputDir = env.getOptions().get(OPT_OUTPUT_DIR);
        Filer filer = env.getFiler();
        this.schemaWriter = new SchemaWriter(filer, pretty, outputDir);

        if (parseBoolean(env.getOptions().get(OPT_GENERATE_CONSTANTS), false)) {
            this.constantsGenerator = new ConstantsClassGenerator(filer);
        }
        if (parseBoolean(env.getOptions().get(OPT_GENERATE_REGISTRY), false)) {
            String fqn = env.getOptions().get(OPT_REGISTRY_CLASS);
            if (fqn == null || fqn.isEmpty()) {
                fqn = DEFAULT_REGISTRY_FQN;
            }
            this.registryGenerator = new SchemaRegistryGenerator(filer, fqn);
        }
    }

    private List<AnnotationMapper> buildMappers(ProcessingEnvironment env) {
        Elements elements = env.getElementUtils();
        boolean includeJackson = parseBoolean(env.getOptions().get(OPT_INCLUDE_JACKSON), true);
        boolean includeJsr303 = parseBoolean(env.getOptions().get(OPT_INCLUDE_JSR303), true);
        boolean includeNullness = parseBoolean(env.getOptions().get(OPT_INCLUDE_NULLNESS), true);

        List<AnnotationMapper> chain = BuiltinMappers.create(
                elements, includeJackson, includeJsr303, includeNullness);
        try {
            for (AnnotationMapper extension :
                    ServiceLoader.load(AnnotationMapper.class, getClass().getClassLoader())) {
                extension.init(env);
                chain.add(extension);
            }
        } catch (ServiceConfigurationError e) {
            messager.printMessage(Diagnostic.Kind.WARNING,
                    "Failed to load AnnotationMapper SPI extensions: " + e.getMessage());
        }
        return chain;
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) {
            flushRegistry();
            return false;
        }
        for (Element element : roundEnv.getElementsAnnotatedWith(JsonSchema.class)) {
            if (element.getKind() != ElementKind.CLASS) {
                messager.printMessage(Diagnostic.Kind.ERROR,
                        "@JsonSchema is only supported on classes", element);
                continue;
            }
            TypeElement typeElement = (TypeElement) element;
            try {
                SchemaDocument document = schemaBuilder.build(typeElement);
                schemaWriter.write(typeElement, document);
                if (constantsGenerator != null || registryGenerator != null) {
                    String compact = new JsonWriter(false).write(document);
                    if (constantsGenerator != null) {
                        constantsGenerator.generate(typeElement, compact);
                    }
                    if (registryGenerator != null) {
                        registryGenerator.register(typeElement, compact);
                    }
                }
            } catch (IOException e) {
                messager.printMessage(Diagnostic.Kind.ERROR,
                        "Failed to write JSON Schema for "
                                + typeElement.getQualifiedName() + ": " + e.getMessage());
            } catch (RuntimeException e) {
                messager.printMessage(Diagnostic.Kind.ERROR,
                        "JSON Schema generation failed for "
                                + typeElement.getQualifiedName() + ": " + e.getMessage());
            }
        }
        return false;
    }

    private void flushRegistry() {
        if (registryGenerator == null || !registryGenerator.hasEntries()) {
            return;
        }
        try {
            registryGenerator.writeRegistryFile();
        } catch (IOException e) {
            messager.printMessage(Diagnostic.Kind.ERROR,
                    "Failed to write JSON Schema registry: " + e.getMessage());
        }
    }

    private static boolean parseBoolean(String raw, boolean fallback) {
        if (raw == null || raw.isEmpty()) {
            return fallback;
        }
        return Boolean.parseBoolean(raw);
    }
}
