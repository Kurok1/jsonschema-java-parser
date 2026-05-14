package io.github.kurok1.jsonschema.examples;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;

public class Product {

    @NotBlank
    @Pattern(regexp = "[A-Z0-9-]{3,16}")
    private String sku;

    @NotBlank
    private String name;

    private String description;
}
