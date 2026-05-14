package io.github.kurok1.jsonschema.core.model;

/**
 * Standard JSON Schema {@code format} keyword values.
 *
 * <p>Mapped from common Java date/time, UUID and URI types by the annotation processor.
 */
public final class StandardFormats {

    public static final String DATE_TIME = "date-time";
    public static final String DATE = "date";
    public static final String TIME = "time";
    public static final String DURATION = "duration";
    public static final String EMAIL = "email";
    public static final String UUID = "uuid";
    public static final String URI = "uri";
    public static final String IPV4 = "ipv4";
    public static final String IPV6 = "ipv6";

    private StandardFormats() {
    }
}
