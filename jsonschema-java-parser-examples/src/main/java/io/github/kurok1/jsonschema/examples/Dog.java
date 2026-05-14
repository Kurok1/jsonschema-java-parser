package io.github.kurok1.jsonschema.examples;

import io.github.kurok1.jsonschema.annotations.JsonSchema;

import javax.validation.constraints.Size;

@JsonSchema(
        title = "Dog",
        description = "Concrete subclass demonstrating default ALL_OF inheritance: parent fields live in $defs and the child references them via allOf."
)
public class Dog extends Animal {

    @Size(min = 1, max = 64)
    private String breed;

    private boolean goodBoy;
}
