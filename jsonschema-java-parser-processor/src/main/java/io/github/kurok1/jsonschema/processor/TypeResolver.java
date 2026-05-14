package io.github.kurok1.jsonschema.processor;

import io.github.kurok1.jsonschema.core.model.SchemaNode;
import io.github.kurok1.jsonschema.core.model.SchemaType;
import io.github.kurok1.jsonschema.core.model.StandardFormats;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.ArrayList;
import java.util.List;

/**
 * Translates {@link TypeMirror} values into {@link SchemaNode}s.
 *
 * <p>Custom user types (anything not in the scalar / collection / map / enum
 * tables and not in the {@code java.*} or {@code javax.*} namespaces) are
 * registered with the supplied {@link TypeRegistry} and rendered as
 * {@code {"$ref": "#/$defs/<fqn>"}}.
 */
final class TypeResolver {

    private final Types types;
    private final Elements elements;

    TypeResolver(ProcessingEnvironment env) {
        this.types = env.getTypeUtils();
        this.elements = env.getElementUtils();
    }

    SchemaNode resolve(TypeMirror type, TypeRegistry registry) {
        TypeKind kind = type.getKind();

        if (kind.isPrimitive()) {
            return resolvePrimitive(kind);
        }
        if (kind == TypeKind.ARRAY) {
            ArrayType arrayType = (ArrayType) type;
            return SchemaNode.builder()
                    .type(SchemaType.ARRAY)
                    .items(resolve(arrayType.getComponentType(), registry))
                    .build();
        }
        if (kind != TypeKind.DECLARED) {
            return SchemaNode.builder().build();
        }

        DeclaredType declared = (DeclaredType) type;
        TypeElement element = (TypeElement) declared.asElement();
        String fqn = element.getQualifiedName().toString();

        SchemaNode scalar = resolveScalar(fqn);
        if (scalar != null) {
            return scalar;
        }
        if (element.getKind() == ElementKind.ENUM) {
            return resolveEnum(element);
        }
        if (isSubtype(declared, "java.util.Map")) {
            return resolveMap(declared, registry);
        }
        if (isSubtype(declared, "java.util.Collection") || isSubtype(declared, "java.lang.Iterable")) {
            return resolveCollection(declared, registry, isSubtype(declared, "java.util.Set"));
        }
        if (fqn.startsWith("java.") || fqn.startsWith("javax.")) {
            return SchemaNode.builder().type(SchemaType.OBJECT).build();
        }
        String key = registry.register(element);
        return SchemaNode.ref(TypeRegistry.refFor(key));
    }

    private SchemaNode resolvePrimitive(TypeKind kind) {
        switch (kind) {
            case BOOLEAN:
                return SchemaNode.builder().type(SchemaType.BOOLEAN).build();
            case BYTE:
            case SHORT:
            case INT:
            case LONG:
            case CHAR:
                return SchemaNode.builder().type(SchemaType.INTEGER).build();
            case FLOAT:
            case DOUBLE:
                return SchemaNode.builder().type(SchemaType.NUMBER).build();
            default:
                return SchemaNode.builder().build();
        }
    }

    private SchemaNode resolveScalar(String fqn) {
        switch (fqn) {
            case "java.lang.String":
            case "java.lang.CharSequence":
            case "java.lang.Character":
                return SchemaNode.builder().type(SchemaType.STRING).build();
            case "java.lang.Boolean":
                return SchemaNode.builder().type(SchemaType.BOOLEAN).build();
            case "java.lang.Byte":
            case "java.lang.Short":
            case "java.lang.Integer":
            case "java.lang.Long":
            case "java.math.BigInteger":
                return SchemaNode.builder().type(SchemaType.INTEGER).build();
            case "java.lang.Float":
            case "java.lang.Double":
            case "java.math.BigDecimal":
                return SchemaNode.builder().type(SchemaType.NUMBER).build();
            case "java.util.Date":
            case "java.time.Instant":
            case "java.time.OffsetDateTime":
            case "java.time.ZonedDateTime":
            case "java.time.LocalDateTime":
                return stringWithFormat(StandardFormats.DATE_TIME);
            case "java.time.LocalDate":
                return stringWithFormat(StandardFormats.DATE);
            case "java.time.LocalTime":
            case "java.time.OffsetTime":
                return stringWithFormat(StandardFormats.TIME);
            case "java.time.Duration":
            case "java.time.Period":
                return stringWithFormat(StandardFormats.DURATION);
            case "java.util.UUID":
                return stringWithFormat(StandardFormats.UUID);
            case "java.net.URI":
            case "java.net.URL":
                return stringWithFormat(StandardFormats.URI);
            default:
                return null;
        }
    }

    private SchemaNode stringWithFormat(String format) {
        return SchemaNode.builder().type(SchemaType.STRING).format(format).build();
    }

    private SchemaNode resolveEnum(TypeElement element) {
        List<String> constants = new ArrayList<>();
        for (Element enclosed : element.getEnclosedElements()) {
            if (enclosed.getKind() == ElementKind.ENUM_CONSTANT) {
                constants.add(enclosed.getSimpleName().toString());
            }
        }
        return SchemaNode.builder()
                .type(SchemaType.STRING)
                .enumValues(constants)
                .build();
    }

    private SchemaNode resolveCollection(DeclaredType declared, TypeRegistry registry, boolean unique) {
        SchemaNode items;
        List<? extends TypeMirror> args = declared.getTypeArguments();
        if (args.isEmpty()) {
            items = SchemaNode.builder().build();
        } else {
            items = resolve(args.get(0), registry);
        }
        return SchemaNode.builder()
                .type(SchemaType.ARRAY)
                .items(items)
                .uniqueItems(unique)
                .build();
    }

    private SchemaNode resolveMap(DeclaredType declared, TypeRegistry registry) {
        SchemaNode valueSchema;
        List<? extends TypeMirror> args = declared.getTypeArguments();
        if (args.size() < 2) {
            valueSchema = SchemaNode.builder().build();
        } else {
            valueSchema = resolve(args.get(1), registry);
        }
        return SchemaNode.builder()
                .type(SchemaType.OBJECT)
                .additionalPropertiesSchema(valueSchema)
                .build();
    }

    private boolean isSubtype(TypeMirror type, String fqn) {
        TypeElement element = elements.getTypeElement(fqn);
        if (element == null) {
            return false;
        }
        TypeMirror erasedTarget = types.erasure(element.asType());
        TypeMirror erasedActual = types.erasure(type);
        return types.isSubtype(erasedActual, erasedTarget);
    }

    Types types() {
        return types;
    }

    Elements elements() {
        return elements;
    }

    static boolean isInstanceField(VariableElement field) {
        return field.getKind() == ElementKind.FIELD
                && !field.getModifiers().contains(javax.lang.model.element.Modifier.STATIC)
                && !field.getModifiers().contains(javax.lang.model.element.Modifier.TRANSIENT);
    }
}
