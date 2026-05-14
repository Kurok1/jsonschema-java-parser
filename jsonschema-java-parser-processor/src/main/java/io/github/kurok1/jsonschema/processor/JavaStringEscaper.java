package io.github.kurok1.jsonschema.processor;

/**
 * Escapes a raw string so it can be embedded as a Java string literal.
 * Used by the constants and registry generators when inlining schema JSON
 * into generated source files.
 */
final class JavaStringEscaper {

    private JavaStringEscaper() {
    }

    static String escape(String s) {
        StringBuilder b = new StringBuilder(s.length() + 16);
        int len = s.length();
        for (int i = 0; i < len; i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\':
                    b.append("\\\\");
                    break;
                case '"':
                    b.append("\\\"");
                    break;
                case '\n':
                    b.append("\\n");
                    break;
                case '\r':
                    b.append("\\r");
                    break;
                case '\t':
                    b.append("\\t");
                    break;
                case '\b':
                    b.append("\\b");
                    break;
                case '\f':
                    b.append("\\f");
                    break;
                default:
                    if (c < 0x20) {
                        b.append(String.format("\\u%04x", (int) c));
                    } else {
                        b.append(c);
                    }
            }
        }
        return b.toString();
    }
}
