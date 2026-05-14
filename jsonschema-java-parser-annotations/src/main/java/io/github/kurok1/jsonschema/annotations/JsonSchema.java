package io.github.kurok1.jsonschema.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.CLASS)
public @interface JsonSchema {

    String id() default "";

    String title() default "";

    String description() default "";

    Draft draft() default Draft.DRAFT_2020_12;

    String outputPath() default "";

    boolean additionalProperties() default false;

    InheritanceStrategy inheritance() default InheritanceStrategy.ALL_OF;

    boolean openaiCompatible() default false;
}
