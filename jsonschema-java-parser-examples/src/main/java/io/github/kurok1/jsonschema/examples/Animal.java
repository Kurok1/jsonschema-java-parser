package io.github.kurok1.jsonschema.examples;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Positive;

public abstract class Animal {

    @NotBlank
    private String name;

    @Positive
    private int ageInMonths;

    private boolean vaccinated;
}
