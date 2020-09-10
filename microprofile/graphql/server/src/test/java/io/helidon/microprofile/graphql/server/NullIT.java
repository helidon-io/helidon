package io.helidon.microprofile.graphql.server;

import java.io.IOException;
import java.util.Map;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import io.helidon.microprofile.graphql.server.test.db.TestDB;
import io.helidon.microprofile.graphql.server.test.queries.QueriesWithNulls;
import io.helidon.microprofile.graphql.server.test.queries.SimpleQueriesWithSource;
import io.helidon.microprofile.graphql.server.test.types.NullPOJO;
import io.helidon.microprofile.graphql.server.test.types.SimpleContact;
import org.jboss.weld.junit5.WeldInitiator;
import org.jboss.weld.junit5.WeldJunit5Extension;
import org.jboss.weld.junit5.WeldSetup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(WeldJunit5Extension.class)
public class NullIT extends AbstractGraphQLIT {

    @WeldSetup
    private final WeldInitiator weld = WeldInitiator.of(WeldInitiator.createWeld()
                                                                .addBeanClass(NullPOJO.class)
                                                                .addBeanClass(QueriesWithNulls.class)
                                                                .addBeanClass(TestDB.class)
                                                                .addExtension(new GraphQLCdiExtension()));

    @Test
    public void testNulls() throws IOException {
        setupIndex(indexFileName, NullPOJO.class, QueriesWithNulls.class);
        ExecutionContext executionContext =  new ExecutionContext(defaultContext);
        Schema schema = executionContext.getSchema();
        assertThat(schema, is(notNullValue()));

        // test primitives should be not null be default
        SchemaType type = schema.getTypeByName("NullPOJO");
        assertReturnTypeMandatory(type, "id", true);
        assertReturnTypeMandatory(type, "longValue", false);
        assertReturnTypeMandatory(type, "stringValue", true);
        assertReturnTypeMandatory(type, "testNullWithGet", true);
        assertReturnTypeMandatory(type, "listNonNullStrings", false);
        assertArrayReturnTypeMandatory(type, "listNonNullStrings", true);
        assertArrayReturnTypeMandatory(type, "listOfListOfNonNullStrings", true);
        assertReturnTypeMandatory(type, "listOfListOfNonNullStrings", false);
        assertReturnTypeMandatory(type, "listOfListOfNullStrings", false);
        assertArrayReturnTypeMandatory(type, "listOfListOfNullStrings", false);
        assertReturnTypeMandatory(type, "testNullWithSet", false);
        assertReturnTypeMandatory(type, "listNullStringsWhichIsMandatory", true);
        assertArrayReturnTypeMandatory(type, "listNullStringsWhichIsMandatory", false);
        assertReturnTypeMandatory(type, "testInputOnly", false);
        assertArrayReturnTypeMandatory(type, "testInputOnly", false);
        assertReturnTypeMandatory(type, "testOutputOnly", false);
        assertArrayReturnTypeMandatory(type, "testOutputOnly", true);

        SchemaType query = schema.getTypeByName("Query");
        assertReturnTypeMandatory(query, "method1NotNull", true);
        assertReturnTypeMandatory(query, "method2NotNull", true);
        assertReturnTypeMandatory(query, "method3NotNull", false);

        assertReturnTypeArgumentMandatory(query, "paramShouldBeNonMandatory", "value", false);
        assertReturnTypeArgumentMandatory(query, "paramShouldBeNonMandatory2", "value", false);
        assertReturnTypeArgumentMandatory(query, "paramShouldBeNonMandatory3", "value", false);

        SchemaType input = schema.getInputTypeByName("NullPOJOInput");
        assertReturnTypeMandatory(input, "nonNullForInput", true);
        assertReturnTypeMandatory(input, "testNullWithGet", false);
        assertReturnTypeMandatory(input, "testNullWithSet", true);
        assertReturnTypeMandatory(input, "listNonNullStrings", false);
        assertArrayReturnTypeMandatory(input, "listNonNullStrings", true);

        assertArrayReturnTypeMandatory(input, "listOfListOfNonNullStrings", true);

        assertReturnTypeMandatory(input, "testInputOnly", false);
        assertArrayReturnTypeMandatory(input, "testInputOnly", true);

        assertReturnTypeMandatory(input, "testOutputOnly", false);
        assertArrayReturnTypeMandatory(input, "testOutputOnly", false);
    }

}
