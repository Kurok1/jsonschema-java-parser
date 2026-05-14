package io.github.kurok1.jsonschema.examples;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import java.math.BigDecimal;

public class LineItem {

    @NotNull
    private Product product;

    @Positive
    private int quantity;

    @NotNull
    private BigDecimal unitPrice;
}
