package io.github.kurok1.jsonschema.examples;

import javax.validation.constraints.DecimalMax;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotBlank;
import java.math.BigDecimal;

public class Discount {

    @NotBlank
    private String code;

    @DecimalMin("0.0")
    @DecimalMax("1.0")
    private BigDecimal fraction;
}
