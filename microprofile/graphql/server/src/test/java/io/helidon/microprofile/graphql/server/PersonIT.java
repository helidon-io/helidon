package io.helidon.microprofile.graphql.server;

import io.helidon.microprofile.graphql.server.AbstractGraphQLIT;
import io.helidon.microprofile.graphql.server.GraphQLCdiExtension;
import io.helidon.microprofile.graphql.server.Schema;
import io.helidon.microprofile.graphql.server.SchemaGenerator;
import io.helidon.microprofile.graphql.server.test.types.Person;
import io.helidon.microprofile.graphql.server.test.types.PersonWithName;
import org.jboss.weld.junit5.WeldInitiator;
import org.jboss.weld.junit5.WeldJunit5Extension;
import org.jboss.weld.junit5.WeldSetup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import java.beans.IntrospectionException;
import java.io.IOException;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

@ExtendWith(WeldJunit5Extension.class)
public class PersonIT extends AbstractGraphQLIT {

    @WeldSetup
    private final WeldInitiator weld = WeldInitiator.of(WeldInitiator.createWeld()
                                                                .addBeanClass(Person.class)
                                                                .addExtension(new GraphQLCdiExtension()));

    /**
     * Test generation of Type with no-name.
     */
    @Test
    public void testTypeGenerationWithNoName() throws IntrospectionException, ClassNotFoundException, IOException {
        setupIndex(indexFileName, Person.class);
        SchemaGenerator schemaGenerator = new SchemaGenerator(defaultContext);
        Schema schema = schemaGenerator.generateSchema();
        assertThat(schema.getTypeByName("Person"), is(notNullValue()));
        assertThat(schema.getTypeByName("Address"), is(notNullValue()));
        assertThat(schema.containsScalarWithName("Date"), is(notNullValue()));
        assertThat(schema.containsScalarWithName("BigDecimal"), is(notNullValue()));
        generateGraphQLSchema(schema);
    }

    /**
     * Test generation of Type with a different name then class name.
     */
    @Test
    public void testPersonWithName() throws IOException, IntrospectionException, ClassNotFoundException {
        setupIndex(indexFileName, PersonWithName.class);
        SchemaGenerator schemaGenerator = new SchemaGenerator(defaultContext);
        Schema schema = schemaGenerator.generateSchema();

        assertThat(schema, is(notNullValue()));
        assertThat(schema.getTypeByName("Person"), is(notNullValue()));
        generateGraphQLSchema(schema);
    }
}
