package io.github.kurok1.jsonschema.processor;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.Elements;
import java.math.BigDecimal;

/**
 * Maps Bean Validation (JSR-303 / JSR-380, Jakarta Validation 3.x) constraints
 * to JSON Schema validation keywords. Detection is FQN-based so the processor
 * doesn't pull either {@code javax.validation} or {@code jakarta.validation}
 * onto user classpaths.
 */
final class ValidationMapper implements AnnotationMapper {

    private static final String[] NOT_NULL = pair("NotNull");
    private static final String[] NOT_EMPTY = pair("NotEmpty");
    private static final String[] NOT_BLANK = pair("NotBlank");
    private static final String[] SIZE = pair("Size");
    private static final String[] MIN = pair("Min");
    private static final String[] MAX = pair("Max");
    private static final String[] DECIMAL_MIN = pair("DecimalMin");
    private static final String[] DECIMAL_MAX = pair("DecimalMax");
    private static final String[] PATTERN = pair("Pattern");
    private static final String[] EMAIL = pair("Email");
    private static final String[] POSITIVE = pair("Positive");
    private static final String[] POSITIVE_OR_ZERO = pair("PositiveOrZero");
    private static final String[] NEGATIVE = pair("Negative");
    private static final String[] NEGATIVE_OR_ZERO = pair("NegativeOrZero");

    private static String[] pair(String simple) {
        return new String[]{
                "javax.validation.constraints." + simple,
                "jakarta.validation.constraints." + simple
        };
    }

    private final Elements elements;

    ValidationMapper(Elements elements) {
        this.elements = elements;
    }

    @Override
    public void apply(VariableElement field, FieldContext ctx) {
        if (Annotations.present(field, NOT_NULL)) {
            ctx.markRequired();
        }
        applyNotEmpty(field, ctx);
        applyNotBlank(field, ctx);
        applySize(field, ctx);
        applyIntegerBound(field, ctx, MIN, "minimum");
        applyIntegerBound(field, ctx, MAX, "maximum");
        applyDecimalBound(field, ctx, DECIMAL_MIN, true);
        applyDecimalBound(field, ctx, DECIMAL_MAX, false);
        applyPattern(field, ctx);
        applyEmail(field, ctx);
        applyZeroBound(field, ctx, POSITIVE, "exclusiveMinimum");
        applyZeroBound(field, ctx, POSITIVE_OR_ZERO, "minimum");
        applyZeroBound(field, ctx, NEGATIVE, "exclusiveMaximum");
        applyZeroBound(field, ctx, NEGATIVE_OR_ZERO, "maximum");
    }

    private void applyNotEmpty(VariableElement field, FieldContext ctx) {
        if (!Annotations.present(field, NOT_EMPTY)) {
            return;
        }
        ctx.markRequired();
        String type = ctx.schemaType();
        if ("string".equals(type)) {
            ctx.builder().keyword("minLength", 1);
        } else if ("array".equals(type)) {
            ctx.builder().keyword("minItems", 1);
        } else if ("object".equals(type)) {
            ctx.builder().keyword("minProperties", 1);
        }
    }

    private void applyNotBlank(VariableElement field, FieldContext ctx) {
        if (!Annotations.present(field, NOT_BLANK)) {
            return;
        }
        ctx.markRequired();
        if ("string".equals(ctx.schemaType())) {
            ctx.builder().keyword("minLength", 1);
        }
    }

    private void applySize(VariableElement field, FieldContext ctx) {
        AnnotationMirror size = Annotations.findAny(field, SIZE);
        if (size == null) {
            return;
        }
        Object minRaw = Annotations.value(elements, size, "min");
        Object maxRaw = Annotations.value(elements, size, "max");
        int min = (minRaw instanceof Integer) ? (Integer) minRaw : 0;
        int max = (maxRaw instanceof Integer) ? (Integer) maxRaw : Integer.MAX_VALUE;
        String type = ctx.schemaType();
        if ("string".equals(type)) {
            if (min > 0) {
                ctx.builder().keyword("minLength", min);
            }
            if (max < Integer.MAX_VALUE) {
                ctx.builder().keyword("maxLength", max);
            }
        } else if ("array".equals(type)) {
            if (min > 0) {
                ctx.builder().keyword("minItems", min);
            }
            if (max < Integer.MAX_VALUE) {
                ctx.builder().keyword("maxItems", max);
            }
        } else if ("object".equals(type)) {
            if (min > 0) {
                ctx.builder().keyword("minProperties", min);
            }
            if (max < Integer.MAX_VALUE) {
                ctx.builder().keyword("maxProperties", max);
            }
        }
    }

    private void applyIntegerBound(VariableElement field, FieldContext ctx,
                                   String[] fqns, String keyword) {
        AnnotationMirror mirror = Annotations.findAny(field, fqns);
        if (mirror == null) {
            return;
        }
        if (!isNumericType(ctx.schemaType())) {
            return;
        }
        Object raw = Annotations.value(elements, mirror, "value");
        if (raw instanceof Long) {
            ctx.builder().keyword(keyword, raw);
        }
    }

    private void applyDecimalBound(VariableElement field, FieldContext ctx,
                                   String[] fqns, boolean isMin) {
        AnnotationMirror mirror = Annotations.findAny(field, fqns);
        if (mirror == null) {
            return;
        }
        if (!isNumericType(ctx.schemaType())) {
            return;
        }
        Object raw = Annotations.value(elements, mirror, "value");
        Object inclusiveRaw = Annotations.value(elements, mirror, "inclusive");
        boolean inclusive = !Boolean.FALSE.equals(inclusiveRaw);
        if (!(raw instanceof String) || ((String) raw).isEmpty()) {
            return;
        }
        BigDecimal value;
        try {
            value = new BigDecimal((String) raw);
        } catch (NumberFormatException e) {
            return;
        }
        String keyword;
        if (isMin) {
            keyword = inclusive ? "minimum" : "exclusiveMinimum";
        } else {
            keyword = inclusive ? "maximum" : "exclusiveMaximum";
        }
        ctx.builder().keyword(keyword, value);
    }

    private void applyPattern(VariableElement field, FieldContext ctx) {
        AnnotationMirror mirror = Annotations.findAny(field, PATTERN);
        if (mirror == null || !"string".equals(ctx.schemaType())) {
            return;
        }
        Object regexp = Annotations.value(elements, mirror, "regexp");
        if (regexp instanceof String && !((String) regexp).isEmpty()) {
            ctx.builder().keyword("pattern", regexp);
        }
    }

    private void applyEmail(VariableElement field, FieldContext ctx) {
        if (Annotations.present(field, EMAIL) && "string".equals(ctx.schemaType())) {
            ctx.builder().format("email");
        }
    }

    private void applyZeroBound(VariableElement field, FieldContext ctx,
                                String[] fqns, String keyword) {
        if (Annotations.present(field, fqns) && isNumericType(ctx.schemaType())) {
            ctx.builder().keyword(keyword, 0);
        }
    }

    private boolean isNumericType(String schemaType) {
        return "integer".equals(schemaType) || "number".equals(schemaType);
    }
}
