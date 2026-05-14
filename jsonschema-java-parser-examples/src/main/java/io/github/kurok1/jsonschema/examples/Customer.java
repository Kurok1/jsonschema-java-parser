package io.github.kurok1.jsonschema.examples;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotNull;
import java.util.UUID;

public class Customer {

    @NotNull
    private UUID id;

    private String fullName;

    @Email
    private String contactEmail;

    private Address billingAddress;
}
