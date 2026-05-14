package io.github.kurok1.jsonschema.examples;

import io.github.kurok1.jsonschema.generated.JsonSchemaRegistry;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class GeneratedSchemaAccessTest {

    @Test
    void schemaIsAvailableFromMetaInfResource() throws IOException {
        String schema;
        try (InputStream in = getClass().getClassLoader()
                .getResourceAsStream("META-INF/jsonschema/io.github.kurok1.jsonschema.examples.User.json")) {
            assertThat(in).as("generated schema resource").isNotNull();
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                int ch;
                while ((ch = reader.read()) != -1) {
                    sb.append((char) ch);
                }
            }
            schema = sb.toString();
        }
        assertThat(schema)
                .contains("\"$schema\": \"https://json-schema.org/draft/2020-12/schema\"")
                .contains("\"display_name\":")
                .contains("\"format\": \"uuid\"")
                .contains("\"$ref\": \"#/$defs/io.github.kurok1.jsonschema.examples.Address\"")
                .contains("\"$defs\":");
    }

    @Test
    void registryReturnsSchemaForKnownClass() {
        String schema = JsonSchemaRegistry.get(User.class);
        assertThat(schema)
                .isNotNull()
                .contains("\"display_name\":")
                .contains("\"$defs\":");
        assertThat(JsonSchemaRegistry.registered()).contains(User.class);
    }
}
