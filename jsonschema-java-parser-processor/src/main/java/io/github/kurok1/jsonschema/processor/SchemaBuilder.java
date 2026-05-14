package io.github.kurok1.jsonschema.processor;

import io.github.kurok1.jsonschema.annotations.Draft;
import io.github.kurok1.jsonschema.annotations.InheritanceStrategy;
import io.github.kurok1.jsonschema.annotations.JsonSchema;
import io.github.kurok1.jsonschema.core.model.SchemaDocument;
import io.github.kurok1.jsonschema.core.model.SchemaNode;
import io.github.kurok1.jsonschema.core.model.SchemaType;
import io.github.kurok1.jsonschema.core.openai.OpenAiCompatibilityTransformer;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts a {@link TypeElement} annotated with {@link JsonSchema} into a
 * {@link SchemaDocument}, expanding referenced custom types into {@code $defs},
 * emitting class hierarchies via {@code allOf} or flat inlining, and delegating
 * per-field annotation handling to a chain of {@link AnnotationMapper}s.
 */
final class SchemaBuilder {

    private final TypeResolver typeResolver;
    private final List<AnnotationMapper> mappers;
    private final boolean globalOpenaiCompatible;

    private boolean currentOpenaiCompatible;

    SchemaBuilder(TypeResolver typeResolver, List<AnnotationMapper> mappers,
                  boolean globalOpenaiCompatible) {
        this.typeResolver = typeResolver;
        this.mappers = mappers;
        this.globalOpenaiCompatible = globalOpenaiCompatible;
    }

    SchemaDocument build(TypeElement root) {
        JsonSchema annotation = root.getAnnotation(JsonSchema.class);
        this.currentOpenaiCompatible = globalOpenaiCompatible || annotation.openaiCompatible();
        InheritanceStrategy strategy = currentOpenaiCompatible
                ? InheritanceStrategy.FLATTEN
                : annotation.inheritance();
        TypeRegistry registry = new TypeRegistry();

        SchemaNode rootSchema = buildClassSchema(root, registry, strategy, annotation);

        Map<String, SchemaNode> defs = new LinkedHashMap<>();
        while (registry.hasPending()) {
            TypeRegistry.PendingEntry entry = registry.nextPending();
            defs.put(entry.fqn(), buildClassSchema(entry.element(), registry, strategy, null));
        }

        Draft draft = annotation.draft();
        SchemaDocument.Builder docBuilder = SchemaDocument.builder()
                .schemaUri(draft.schemaUri())
                .id(annotation.id())
                .root(rootSchema);
        for (Map.Entry<String, SchemaNode> e : defs.entrySet()) {
            docBuilder.addDef(e.getKey(), e.getValue());
        }
        SchemaDocument document = docBuilder.build();
        if (currentOpenaiCompatible) {
            document = new OpenAiCompatibilityTransformer().transform(document);
        }
        return document;
    }

    private SchemaNode buildClassSchema(TypeElement element, TypeRegistry registry,
                                        InheritanceStrategy strategy, JsonSchema meta) {
        TypeElement parent = nonObjectSuperclass(element);
        if (parent == null || strategy == InheritanceStrategy.FLATTEN) {
            return buildAsPlainObject(element, registry, parent != null, meta);
        }
        return buildAsAllOf(element, parent, registry, meta);
    }

    private SchemaNode buildAsPlainObject(TypeElement element, TypeRegistry registry,
                                          boolean walkParents, JsonSchema meta) {
        FieldAccumulator acc = new FieldAccumulator();
        if (walkParents) {
            collectFieldsRecursive(element, registry, acc);
        } else {
            collectOwnFields(element, registry, acc);
        }

        SchemaNode.Builder b = SchemaNode.builder().type(SchemaType.OBJECT);
        if (meta != null) {
            b.title(meta.title()).description(meta.description());
        }
        if (!acc.properties.isEmpty()) {
            b.properties(acc.properties);
        }
        if (!acc.required.isEmpty()) {
            b.required(acc.required);
        }
        boolean additional = meta != null && meta.additionalProperties();
        b.additionalProperties(additional);
        return b.build();
    }

    private SchemaNode buildAsAllOf(TypeElement element, TypeElement parent,
                                    TypeRegistry registry, JsonSchema meta) {
        registry.register(parent);

        FieldAccumulator acc = new FieldAccumulator();
        collectOwnFields(element, registry, acc);

        SchemaNode.Builder innerBuilder = SchemaNode.builder();
        if (!acc.properties.isEmpty()) {
            innerBuilder.properties(acc.properties);
        }
        if (!acc.required.isEmpty()) {
            innerBuilder.required(acc.required);
        }

        List<Object> allOfList = new ArrayList<>(2);
        allOfList.add(SchemaNode.ref(TypeRegistry.refFor(parent.getQualifiedName().toString())));
        allOfList.add(innerBuilder.build());

        SchemaNode.Builder rootBuilder = SchemaNode.builder();
        if (meta != null) {
            rootBuilder.title(meta.title()).description(meta.description());
        }
        rootBuilder.keyword("allOf", allOfList);
        return rootBuilder.build();
    }

    private void collectFieldsRecursive(TypeElement element, TypeRegistry registry, FieldAccumulator acc) {
        TypeElement parent = nonObjectSuperclass(element);
        if (parent != null) {
            collectFieldsRecursive(parent, registry, acc);
        }
        collectOwnFields(element, registry, acc);
    }

    private void collectOwnFields(TypeElement element, TypeRegistry registry, FieldAccumulator acc) {
        for (Element enclosed : element.getEnclosedElements()) {
            if (enclosed.getKind() != ElementKind.FIELD) {
                continue;
            }
            VariableElement field = (VariableElement) enclosed;
            if (!TypeResolver.isInstanceField(field)) {
                continue;
            }
            FieldContext ctx = buildFieldContext(field, registry);
            for (AnnotationMapper mapper : mappers) {
                mapper.apply(field, ctx);
                if (ctx.isIgnored()) {
                    break;
                }
            }
            if (ctx.isIgnored()) {
                continue;
            }
            String name = ctx.nameOverride() != null
                    ? ctx.nameOverride()
                    : field.getSimpleName().toString();
            acc.properties.put(name, ctx.builder().build());
            if (currentOpenaiCompatible) {
                acc.required.add(name);
            } else if (ctx.isRequired() && !ctx.isOptional()) {
                acc.required.add(name);
            }
        }
    }

    private FieldContext buildFieldContext(VariableElement field, TypeRegistry registry) {
        TypeMirror declaredType = field.asType();
        TypeMirror effectiveType = declaredType;
        boolean optional = false;
        if (declaredType.getKind() == TypeKind.DECLARED) {
            DeclaredType dt = (DeclaredType) declaredType;
            TypeElement el = (TypeElement) dt.asElement();
            if ("java.util.Optional".contentEquals(el.getQualifiedName())
                    && !dt.getTypeArguments().isEmpty()) {
                effectiveType = dt.getTypeArguments().get(0);
                optional = true;
            }
        }
        SchemaNode base = typeResolver.resolve(effectiveType, registry);
        SchemaNode.Builder builder = SchemaNode.builder();
        for (Map.Entry<String, Object> e : base.keywords().entrySet()) {
            builder.keyword(e.getKey(), e.getValue());
        }
        FieldContext ctx = new FieldContext(extractSchemaType(base), builder);
        ctx.setOptional(optional);
        return ctx;
    }

    private String extractSchemaType(SchemaNode base) {
        Object t = base.keywords().get("type");
        if (t instanceof String) {
            return (String) t;
        }
        if (t instanceof List) {
            List<?> list = (List<?>) t;
            for (Object item : list) {
                if (item instanceof String && !"null".equals(item)) {
                    return (String) item;
                }
            }
        }
        return null;
    }

    private TypeElement nonObjectSuperclass(TypeElement element) {
        TypeMirror superMirror = element.getSuperclass();
        if (superMirror == null || superMirror.getKind() != TypeKind.DECLARED) {
            return null;
        }
        TypeElement superElement = (TypeElement) ((DeclaredType) superMirror).asElement();
        if ("java.lang.Object".contentEquals(superElement.getQualifiedName())) {
            return null;
        }
        return superElement;
    }

    private static final class FieldAccumulator {
        final Map<String, SchemaNode> properties = new LinkedHashMap<>();
        final List<String> required = new ArrayList<>();
    }
}
