package io.helidon.microprofile.graphql.server;

import java.beans.IntrospectionException;
import java.io.IOException;

import io.helidon.microprofile.graphql.server.test.types.Person;
import io.helidon.microprofile.tests.junit5.AddBean;
import io.helidon.microprofile.tests.junit5.AddExtension;
import io.helidon.microprofile.tests.junit5.DisableDiscovery;
import io.helidon.microprofile.tests.junit5.HelidonTest;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Test valid {@link SchemaPreProcessor}.
 */
@AddBean(Person.class)
@AddBean(CustomSchemaPreProcessor.class)
public class ValidSchemaPreProcessorIT extends AbstractGraphQLIT {

    @Test
    public void testCustomPreProcessor() throws IOException, IntrospectionException, ClassNotFoundException {
        setupIndex(indexFileName, Person.class);
        SchemaGenerator schemaGenerator = new SchemaGenerator(defaultContext);
        Schema schema = schemaGenerator.generateSchema();

        // SchemaPreProcessor should have been called and generated InputType for Person
        assertThat(schema.getInputTypeByName("PersonInput"), is(notNullValue()));
    }
}
