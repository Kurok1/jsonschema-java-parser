package io.github.kurok1.jsonschema.examples;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import io.github.kurok1.jsonschema.annotations.JsonSchema;

import javax.validation.constraints.Email;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@JsonSchema(
        id = "https://example.com/schemas/User",
        title = "User",
        description = "A registered user of the example service."
)
public class User {

    @NotNull
    private UUID id;

    @JsonProperty("display_name")
    @JsonPropertyDescription("Shown in user interfaces.")
    @Size(min = 1, max = 64)
    private String displayName;

    @Email
    private String contactEmail;

    @Min(0)
    private int loginCount;

    private Instant createdAt;

    @Size(max = 16)
    private List<String> tags;

    @NotNull
    private Address primaryAddress;

    private Optional<Address> billingAddress;
}
