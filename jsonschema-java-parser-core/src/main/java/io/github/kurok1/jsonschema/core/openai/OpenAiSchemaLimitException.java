package io.github.kurok1.jsonschema.core.openai;

/**
 * Raised when a transformed schema exceeds OpenAI's strict-mode limits
 * (≤ {@value OpenAiCompatibilityTransformer#MAX_PROPERTIES} properties,
 * ≤ {@value OpenAiCompatibilityTransformer#MAX_DEPTH} nesting levels).
 */
public final class OpenAiSchemaLimitException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public OpenAiSchemaLimitException(String message) {
        super(message);
    }
}
