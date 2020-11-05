package io.helidon.microprofile.graphql.server;

import java.beans.IntrospectionException;
import java.io.IOException;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import io.helidon.microprofile.graphql.server.test.types.Level0;
import io.helidon.microprofile.tests.junit5.AddBean;

import org.junit.jupiter.api.Test;

/**
 * Tests for multi-level object graphs - Level0.
 */
@AddBean(Level0.class)
public class Level0IT extends AbstractGraphQLIT {
    
    @Test
    public void testLevel0() throws IOException, IntrospectionException, ClassNotFoundException {
        setupIndex(indexFileName, Level0.class);
        SchemaGenerator schemaGenerator = createSchemaGenerator(defaultContext);
        Schema schema = schemaGenerator.generateSchema();
        assertThat(schema.containsTypeWithName("Level0"), is(true));
        assertThat(schema.containsTypeWithName("Level1"), is(true));
        assertThat(schema.containsTypeWithName("Level2"), is(true));
        generateGraphQLSchema(schema);
    }

    @Test
    public void testMultipleLevels() throws IOException, IntrospectionException, ClassNotFoundException {
        setupIndex(indexFileName, Level0.class);
        SchemaGenerator schemaGenerator = createSchemaGenerator(defaultContext);
        Schema schema = schemaGenerator.generateSchema();

        assertThat(schema, is(notNullValue()));
        assertThat(schema.getTypes().size(), is(6));
        assertThat(schema.getTypeByName("Level0"), is(notNullValue()));
        assertThat(schema.getTypeByName("Level1"), is(notNullValue()));
        assertThat(schema.getTypeByName("Level2"), is(notNullValue()));
        assertThat(schema.getTypeByName("Address"), is(notNullValue()));
        assertThat(schema.getTypeByName("Query"), is(notNullValue()));
        generateGraphQLSchema(schema);
    }
}
