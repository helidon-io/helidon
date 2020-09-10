package io.helidon.microprofile.graphql.server;

import java.io.IOException;
import java.util.Map;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import io.helidon.microprofile.graphql.server.test.db.TestDB;
import io.helidon.microprofile.graphql.server.test.mutations.SimpleMutations;
import io.helidon.microprofile.graphql.server.test.types.SimpleContactInputType;
import io.helidon.microprofile.graphql.server.test.types.SimpleContactInputTypeWithAddress;
import io.helidon.microprofile.graphql.server.test.types.SimpleContactInputTypeWithName;
import io.helidon.microprofile.graphql.server.test.types.SimpleContactInputTypeWithNameValue;
import org.jboss.weld.junit5.WeldInitiator;
import org.jboss.weld.junit5.WeldJunit5Extension;
import org.jboss.weld.junit5.WeldSetup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(WeldJunit5Extension.class)
public class SimpleMutationsIT extends AbstractGraphQLIT {

    @WeldSetup
    private final WeldInitiator weld = WeldInitiator.of(WeldInitiator.createWeld()
                                                                .addBeanClass(SimpleMutations.class)
                                                                .addBeanClass(TestDB.class)
                                                                .addExtension(new GraphQLCdiExtension()));

    @Test
    @SuppressWarnings("unchecked")
    public void testSimpleMutations() throws IOException {
        setupIndex(indexFileName, SimpleMutations.class);
        ExecutionContext executionContext = new ExecutionContext(defaultContext);

        Map<String, Object> mapResults = getAndAssertResult(
                executionContext.execute("mutation { createNewContact { id name age } }"));
        assertThat(mapResults.size(), is(1));
        Map<String, Object> mapResults2 = (Map<String, Object>) mapResults.get("createNewContact");

        assertThat(mapResults2, is(notNullValue()));
        assertThat(mapResults2.get("id"), is(notNullValue()));
        assertThat(mapResults2.get("name"), is(notNullValue()));
        assertThat(((String) mapResults2.get("name")).startsWith("Name"), is(true));
        assertThat(mapResults2.get("age"), is(notNullValue()));

        mapResults = getAndAssertResult(
                executionContext.execute("mutation { createContactWithName(name: \"tim\") { id name age } }"));
        assertThat(mapResults.size(), is(1));
        mapResults2 = (Map<String, Object>) mapResults.get("createContactWithName");

        assertThat(mapResults2, is(notNullValue()));
        assertThat(mapResults2.get("id"), is(notNullValue()));
        assertThat(mapResults2.get("name"), is("tim"));
        assertThat(mapResults2.get("age"), is(notNullValue()));

        mapResults = getAndAssertResult(executionContext.execute("mutation { echoStringValue(value: \"echo\") }"));
        assertThat(mapResults.size(), is(1));
        assertThat(mapResults.get("echoStringValue"), is("echo"));
    }

}
