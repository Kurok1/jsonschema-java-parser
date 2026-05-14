package io.github.kurok1.jsonschema.examples;

import io.github.kurok1.jsonschema.annotations.JsonSchema;

import javax.validation.constraints.Min;

@JsonSchema(title = "Cat")
public class Cat extends Animal {

    private String coatColor;

    @Min(0)
    private int livesRemaining;
}
