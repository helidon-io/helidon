package io.helidon.microprofile.graphql.server;

@SchemaProcessor
public class CustomSchemaPreProcessor implements SchemaPreProcessor {

    @Override
    public void processSchema(Schema schema) {
        // create a new input type from the Person type
        SchemaInputType inputType = schema.getTypeByName("Person").createInputType("Input");
        schema.addInputType(inputType);
    }
}
