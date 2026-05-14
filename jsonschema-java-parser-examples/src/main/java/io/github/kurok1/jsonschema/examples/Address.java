package io.github.kurok1.jsonschema.examples;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;

public class Address {

    @NotBlank
    private String street;

    @NotBlank
    private String city;

    @Pattern(regexp = "[A-Z]{2}")
    private String country;

    private String postalCode;
}
