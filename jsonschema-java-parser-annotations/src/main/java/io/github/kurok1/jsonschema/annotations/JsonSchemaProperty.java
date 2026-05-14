package io.github.kurok1.jsonschema.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.CLASS)
public @interface JsonSchemaProperty {

    String name() default "";

    String description() default "";

    String format() default "";

    boolean required() default false;

    String defaultValue() default "";

    String[] examples() default {};
}
