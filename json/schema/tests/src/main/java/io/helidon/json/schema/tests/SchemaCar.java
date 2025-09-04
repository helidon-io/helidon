package io.helidon.json.schema.tests;

import io.helidon.json.schema.JsonSchema;

@JsonSchema.Schema
@JsonSchema.Description("My super car")
public record SchemaCar(@JsonSchema.Description("The color of my car") String color) {
}
