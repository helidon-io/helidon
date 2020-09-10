package io.helidon.microprofile.graphql.server;

import java.io.IOException;

import java.util.Map;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.helidon.microprofile.graphql.server.test.db.TestDB;
import io.helidon.microprofile.graphql.server.test.queries.QueriesWithIgnorable;

import io.helidon.microprofile.graphql.server.test.types.ObjectWithIgnorableFieldsAndMethods;
import org.jboss.weld.junit5.WeldInitiator;
import org.jboss.weld.junit5.WeldJunit5Extension;
import org.jboss.weld.junit5.WeldSetup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.opentest4j.AssertionFailedError;

@ExtendWith(WeldJunit5Extension.class)
public class IngorableIT extends AbstractGraphQLIT {

    @WeldSetup
    private final WeldInitiator weld = WeldInitiator.of(WeldInitiator.createWeld()
                                                                .addBeanClass(QueriesWithIgnorable.class)
                                                                .addBeanClass(ObjectWithIgnorableFieldsAndMethods.class)
                                                                .addBeanClass(TestDB.class)
                                                                .addExtension(new GraphQLCdiExtension()));
    
    @Test
    public void testObjectWithIgnorableFields() throws IOException {
        setupIndex(indexFileName, ObjectWithIgnorableFieldsAndMethods.class);
        ExecutionContext executionContext =  new ExecutionContext(defaultContext);
        executionContext.execute("query { hero }");
    }

    @Test
    public void testIgnorable() throws IOException {
        setupIndex(indexFileName, QueriesWithIgnorable.class);
        ExecutionContext executionContext =  new ExecutionContext(defaultContext);
        Map<String, Object> mapResults = getAndAssertResult(
                executionContext.execute("query { testIgnorableFields { id dontIgnore } }"));
        assertThat(mapResults.size(), is(1));

        Map<String, Object> mapResults2 = (Map<String, Object>) mapResults.get("testIgnorableFields");
        assertThat(mapResults2.size(), is(2));
        assertThat(mapResults2.get("id"), is("id"));
        assertThat(mapResults2.get("dontIgnore"), is(true));

        // ensure getting the fields generates an error that is caught by the getAndAssertResult
        assertThrows(AssertionFailedError.class, () -> getAndAssertResult(executionContext
                                                                                  .execute(
                                                                                          "query { testIgnorableFields { id "
                                                                                                  + "dontIgnore pleaseIgnore "
                                                                                                  + "ignoreThisAsWell } }")));

        Schema schema = executionContext.getSchema();
        SchemaType type = schema.getTypeByName("ObjectWithIgnorableFieldsAndMethods");
        assertThat(type, is(notNullValue()));
        assertThat(type.getFieldDefinitions().stream().filter(fd -> fd.getName().equals("ignoreGetMethod")).count(), is(0L));

        SchemaInputType inputType = schema.getInputTypeByName("ObjectWithIgnorableFieldsAndMethodsInput");
        assertThat(inputType, is(notNullValue()));
        assertThat(inputType.getFieldDefinitions().stream().filter(fd -> fd.getName().equals("ignoreBecauseOfMethod")).count(),
                   is(0L));
        assertThat(inputType.getFieldDefinitions().stream().filter(fd -> fd.getName().equals("valueSetter")).count(), is(1L));
    }

}
