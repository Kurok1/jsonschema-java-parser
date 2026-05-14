package io.github.kurok1.jsonschema.examples;

import javax.validation.constraints.NotBlank;

public class Person {

    @NotBlank
    private String firstName;

    @NotBlank
    private String lastName;

    private java.time.LocalDate dateOfBirth;
}
