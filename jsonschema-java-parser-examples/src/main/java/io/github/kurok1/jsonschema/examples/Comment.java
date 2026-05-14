package io.github.kurok1.jsonschema.examples;

import io.github.kurok1.jsonschema.annotations.JsonSchema;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@JsonSchema(
        title = "Comment",
        description = "Demonstrates a recursive POJO reference: 'replies' is a List<Comment> and 'parent' is an Optional<Comment>, so the schema $refs back to itself via $defs."
)
public class Comment {

    @NotNull
    private UUID id;

    @NotBlank
    private String author;

    @NotBlank
    private String body;

    private Instant postedAt;

    private List<Comment> replies;

    private Optional<Comment> parent;
}
