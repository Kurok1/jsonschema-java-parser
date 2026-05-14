package io.github.kurok1.jsonschema.core.json;

import io.github.kurok1.jsonschema.core.model.SchemaDocument;
import io.github.kurok1.jsonschema.core.model.SchemaNode;
import io.github.kurok1.jsonschema.core.model.SchemaType;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JsonWriterTest {

    @Test
    void writesScalarObjectSchema() {
        SchemaNode name = SchemaNode.builder().type(SchemaType.STRING).build();
        SchemaNode age = SchemaNode.builder().type(SchemaType.INTEGER).build();
        Map<String, SchemaNode> props = new LinkedHashMap<>();
        props.put("name", name);
        props.put("age", age);
        SchemaNode root = SchemaNode.builder()
                .type(SchemaType.OBJECT)
                .properties(props)
                .required(Arrays.asList("name"))
                .additionalProperties(false)
                .build();

        String json = new JsonWriter(false).write(root);

        assertThat(json).isEqualTo(
                "{\"type\":\"object\",\"properties\":{\"name\":{\"type\":\"string\"},\"age\":{\"type\":\"integer\"}}"
                        + ",\"required\":[\"name\"],\"additionalProperties\":false}");
    }

    @Test
    void writesDocumentWithSchemaUriAndDefs() {
        SchemaNode userRef = SchemaNode.ref("#/$defs/User");
        SchemaNode userDef = SchemaNode.builder()
                .type(SchemaType.OBJECT)
                .properties(singleProp("id", SchemaNode.builder().type(SchemaType.STRING).build()))
                .build();
        SchemaDocument doc = SchemaDocument.builder()
                .schemaUri("https://json-schema.org/draft/2020-12/schema")
                .id("https://example.com/Order")
                .root(SchemaNode.builder()
                        .type(SchemaType.OBJECT)
                        .properties(singleProp("user", userRef))
                        .build())
                .addDef("User", userDef)
                .build();

        String json = new JsonWriter(false).write(doc);

        assertThat(json).contains("\"$schema\":\"https://json-schema.org/draft/2020-12/schema\"");
        assertThat(json).contains("\"$id\":\"https://example.com/Order\"");
        assertThat(json).contains("\"$defs\":{\"User\":");
        assertThat(json).contains("\"user\":{\"$ref\":\"#/$defs/User\"}");
    }

    @Test
    void writesArraySchemaWithUniqueItems() {
        SchemaNode tags = SchemaNode.builder()
                .type(SchemaType.ARRAY)
                .items(SchemaNode.builder().type(SchemaType.STRING).build())
                .uniqueItems(true)
                .build();

        String json = new JsonWriter(false).write(tags);

        assertThat(json).isEqualTo("{\"type\":\"array\",\"items\":{\"type\":\"string\"},\"uniqueItems\":true}");
    }

    @Test
    void writesEnumSchema() {
        SchemaNode status = SchemaNode.builder()
                .type(SchemaType.STRING)
                .enumValues(Arrays.asList("OPEN", "CLOSED"))
                .build();

        String json = new JsonWriter(false).write(status);

        assertThat(json).isEqualTo("{\"type\":\"string\",\"enum\":[\"OPEN\",\"CLOSED\"]}");
    }

    @Test
    void escapesSpecialCharacters() {
        SchemaNode node = SchemaNode.builder()
                .type(SchemaType.STRING)
                .description("line1\nline2\t\"quoted\"")
                .build();

        String json = new JsonWriter(false).write(node);

        assertThat(json).contains("\"description\":\"line1\\nline2\\t\\\"quoted\\\"\"");
    }

    @Test
    void prettyPrintsWithTwoSpaceIndent() {
        SchemaNode node = SchemaNode.builder()
                .type(SchemaType.OBJECT)
                .properties(singleProp("name", SchemaNode.builder().type(SchemaType.STRING).build()))
                .build();

        String json = new JsonWriter(true).write(node);

        assertThat(json).isEqualTo("{\n"
                + "  \"type\": \"object\",\n"
                + "  \"properties\": {\n"
                + "    \"name\": {\n"
                + "      \"type\": \"string\"\n"
                + "    }\n"
                + "  }\n"
                + "}");
    }

    private static Map<String, SchemaNode> singleProp(String name, SchemaNode value) {
        Map<String, SchemaNode> m = new LinkedHashMap<>();
        m.put(name, value);
        return m;
    }
}
