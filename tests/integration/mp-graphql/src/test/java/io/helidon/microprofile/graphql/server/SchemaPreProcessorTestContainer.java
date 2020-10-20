package io.helidon.microprofile.graphql.server;

import java.beans.IntrospectionException;
import java.io.IOException;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import io.helidon.microprofile.graphql.server.test.types.Person;
import io.helidon.microprofile.tests.junit5.AddBean;
import io.helidon.microprofile.tests.junit5.AddExtension;
import io.helidon.microprofile.tests.junit5.DisableDiscovery;
import io.helidon.microprofile.tests.junit5.HelidonTest;
import org.junit.jupiter.api.Test;

public class SchemaPreProcessorTestContainer {

    /**
     * Test valid {@link SchemaPreProcessor}.
     */
    @HelidonTest
    @DisableDiscovery
    @AddExtension(GraphQLCdiExtension.class)
    @AddBean(Person.class)
    @AddBean(SchemaPreProcessorTestContainer.CustomSchemaPreProcessor.class)
    public static class ValidSchemaPreProcessorIT extends AbstractGraphQLIT {

        @Test
        public void testCustomPreProcessor() throws IOException, IntrospectionException, ClassNotFoundException {
            setupIndex(indexFileName, Person.class);
            SchemaGenerator schemaGenerator = new SchemaGenerator(defaultContext);
            Schema schema = schemaGenerator.generateSchema();

            // SchemaPreProcessor should have been called and generated InputType for Person
            assertThat(schema.getInputTypeByName("PersonInput"), is(notNullValue()));
        }
    }

    /**
     * Test invalid {@link SchemaPreProcessor}.
     */
    @HelidonTest
    @DisableDiscovery
    @AddExtension(GraphQLCdiExtension.class)
    @AddBean(Person.class)
    @AddBean(SchemaPreProcessorTestContainer.InvalidSchemaPreProcessor.class)
    public static class InvalidSchemaPreProcessorIT extends AbstractGraphQLIT {

        @Test
        public void testCustomPreProcessor() throws IOException, IntrospectionException, ClassNotFoundException {
            setupIndex(indexFileName, Person.class);
            SchemaGenerator schemaGenerator = new SchemaGenerator(defaultContext);
            Schema schema = schemaGenerator.generateSchema();

            // should be null as an invalid class was found
            assertThat(schema.getInputTypeByName("PersonInput"), is(nullValue()));
        }
    }

    @SchemaProcessor
    public static class CustomSchemaPreProcessor implements SchemaPreProcessor {

        @Override
        public void processSchema(Schema schema) {
            // create a new input type from the Person type
            SchemaInputType inputType = schema.getTypeByName("Person").createInputType("Input");
            schema.addInputType(inputType);
        }
    }

    /**
     * A {@link SchemaProcessor} which is invalid because it does not implement
     * {@link SchemaPreProcessor}.
     */
    @SchemaProcessor
    public static class InvalidSchemaPreProcessor {
    }
}
