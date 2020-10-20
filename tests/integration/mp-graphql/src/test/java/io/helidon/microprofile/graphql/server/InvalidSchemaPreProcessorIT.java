package io.helidon.microprofile.graphql.server;

import java.beans.IntrospectionException;
import java.io.IOException;

import io.helidon.microprofile.graphql.server.test.types.Person;
import io.helidon.microprofile.tests.junit5.AddBean;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Test invalid {@link SchemaPreProcessor}.
 */
@AddBean(Person.class)
@AddBean(InvalidSchemaPreProcessor.class)
public class InvalidSchemaPreProcessorIT extends AbstractGraphQLIT {

    @Test
    public void testCustomPreProcessor() throws IOException, IntrospectionException, ClassNotFoundException {
        setupIndex(indexFileName, Person.class);
        SchemaGenerator schemaGenerator = new SchemaGenerator(defaultContext);
        Schema schema = schemaGenerator.generateSchema();

        // should be null as an invalid class was found
        assertThat(schema.getInputTypeByName("PersonInput"), is(nullValue()));
    }
}
