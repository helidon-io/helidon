package io.helidon.microprofile.graphql.server;

import java.beans.IntrospectionException;
import java.io.IOException;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import io.helidon.microprofile.graphql.server.AbstractGraphQLIT;
import io.helidon.microprofile.graphql.server.GraphQLCdiExtension;
import io.helidon.microprofile.graphql.server.Schema;
import io.helidon.microprofile.graphql.server.SchemaGenerator;
import io.helidon.microprofile.graphql.server.test.types.Level0;
import io.helidon.microprofile.graphql.server.test.types.Person;
import org.jboss.weld.junit5.WeldInitiator;
import org.jboss.weld.junit5.WeldJunit5Extension;
import org.jboss.weld.junit5.WeldSetup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(WeldJunit5Extension.class)
public class Level0IT extends AbstractGraphQLIT {

    @WeldSetup
    private final WeldInitiator weld = WeldInitiator.of(WeldInitiator.createWeld()
                                                                .addBeanClass(Level0.class)
                                                                .addExtension(new GraphQLCdiExtension()));

    @Test
    public void testLevel0() throws IOException, IntrospectionException, ClassNotFoundException {
        setupIndex(indexFileName, Level0.class);
        SchemaGenerator schemaGenerator = new SchemaGenerator(defaultContext);
        Schema schema = schemaGenerator.generateSchema();
        assertThat(schema.containsTypeWithName("Level0"), is(true));
        assertThat(schema.containsTypeWithName("Level1"), is(true));
        assertThat(schema.containsTypeWithName("Level2"), is(true));
        generateGraphQLSchema(schema);
    }

    @Test
    public void testMultipleLevels() throws IOException, IntrospectionException, ClassNotFoundException {
        setupIndex(indexFileName, Level0.class);
        SchemaGenerator schemaGenerator = new SchemaGenerator(defaultContext);
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
