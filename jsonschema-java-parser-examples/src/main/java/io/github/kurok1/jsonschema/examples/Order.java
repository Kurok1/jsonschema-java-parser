package io.github.kurok1.jsonschema.examples;

import io.github.kurok1.jsonschema.annotations.JsonSchema;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@JsonSchema(
        id = "https://example.com/schemas/Order",
        title = "Order",
        description = "Demonstrates deep POJO references: nested ($ref → $ref → $ref via Customer → Address), array-of-ref (List<LineItem>), map-of-ref (Map<String, Discount>), and enum support."
)
public class Order {

    @NotNull
    private UUID orderId;

    @NotNull
    private Customer customer;

    @NotEmpty
    @Size(max = 256)
    private List<LineItem> items;

    private Map<String, Discount> appliedDiscounts;

    @NotNull
    private Address shippingAddress;

    private Optional<Address> billingAddress;

    @NotNull
    private OrderStatus status;

    private Instant placedAt;
}
