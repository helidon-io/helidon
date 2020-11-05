package io.helidon.microprofile.graphql.server;

import java.io.IOException;
import java.util.Map;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import io.helidon.microprofile.graphql.server.test.db.TestDB;
import io.helidon.microprofile.graphql.server.test.queries.DefaultValueQueries;
import io.helidon.microprofile.graphql.server.test.queries.OddNamedQueriesAndMutations;
import io.helidon.microprofile.graphql.server.test.queries.SimpleQueriesWithSource;
import io.helidon.microprofile.graphql.server.test.types.DefaultValuePOJO;
import io.helidon.microprofile.graphql.server.test.types.SimpleContact;
import org.jboss.weld.junit5.WeldInitiator;
import org.jboss.weld.junit5.WeldJunit5Extension;
import org.jboss.weld.junit5.WeldSetup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(WeldJunit5Extension.class)
public class SourceIT extends AbstractGraphQLIT {

    @WeldSetup
    private final WeldInitiator weld = WeldInitiator.of(WeldInitiator.createWeld()
                                                                .addBeanClass(SimpleQueriesWithSource.class)
                                                                .addBeanClass(SimpleContact.class)
                                                                .addBeanClass(TestDB.class)
                                                                .addExtension(new GraphQLCdiExtension()));

    @Test
    @SuppressWarnings("unchecked")
    public void testSimpleQueriesWithSource() throws IOException {
        setupIndex(indexFileName, SimpleQueriesWithSource.class, SimpleContact.class);
        ExecutionContext executionContext =  new ExecutionContext(defaultContext);

        // since there is a @Source annotation in SimpleQueriesWithSource, then this should add a field
        // idAndName to the SimpleContact type
        Map<String, Object> mapResults = getAndAssertResult(executionContext.execute("query { findContact { id idAndName } }"));

        assertThat(mapResults.size(), is(1));
        Map<String, Object> mapResults2 = (Map<String, Object>) mapResults.get("findContact");
        assertThat(mapResults2, is(notNullValue()));
        assertThat(mapResults2.get("id"), is(notNullValue()));
        assertThat(mapResults2.get("idAndName"), is(notNullValue()));

        // test the query at the top level
        SimpleContact contact1 = new SimpleContact("c1", "Contact 1", 50);

        String json = "contact: " + getContactAsQueryInput(contact1);

        mapResults = getAndAssertResult(executionContext.execute("query { currentJob (" + json + ") }"));
        assertThat(mapResults.size(), is(1));
        String currentJob = (String) mapResults.get("currentJob");
        assertThat(currentJob, is(notNullValue()));

        // test the query from the object
        mapResults = getAndAssertResult(executionContext.execute("query { findContact { id idAndName currentJob } }"));
        assertThat(mapResults.size(), is(1));
        mapResults2 = (Map<String, Object>) mapResults.get("findContact");
        assertThat(mapResults2, is(notNullValue()));
        assertThat(mapResults2.get("id"), is(notNullValue()));
        assertThat(mapResults2.get("idAndName"), is(notNullValue()));
        assertThat(mapResults2.get("currentJob"), is(notNullValue()));

        // test the query from the object
        mapResults = getAndAssertResult(executionContext.execute("query { findContact { id lastNAddress(count: 1) { city } } }"));
        assertThat(mapResults.size(), is(1));
        mapResults2 = (Map<String, Object>) mapResults.get("findContact");
        assertThat(mapResults2, is(notNullValue()));
        assertThat(mapResults2.get("id"), is(notNullValue()));
        assertThat(mapResults2.get("lastNAddress"), is(notNullValue()));
    }

}
