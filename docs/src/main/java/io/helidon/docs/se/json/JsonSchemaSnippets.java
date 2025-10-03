package io.helidon.docs.se.json;

import io.helidon.json.schema.JsonSchema;
import io.helidon.json.schema.Schema;

class JsonSchemaSnippets {

    void jsonSchemaProgrammaticSnippet() {
        // tag::snippet_1[]
        Schema.builder()
                .rootObject(builder -> builder.description("Example JSON Schema")
                        .addIntegerProperty("exampleProperty", intBuilder -> intBuilder.minimum(0)))
                .build();
        // end::snippet_1[]
    }

    // tag::snippet_2[]
    @JsonSchema.Schema // <1>
    @JsonSchema.Description("Example JSON Schema")
    public record ExampleSchema(@JsonSchema.Integer.Minimum(0) int exampleProperty) {
    }
    // end::snippet_2[]

}
