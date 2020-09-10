package io.helidon.microprofile.graphql.server;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import io.helidon.microprofile.graphql.server.AbstractGraphQLIT;
import io.helidon.microprofile.graphql.server.ExecutionContext;
import io.helidon.microprofile.graphql.server.GraphQLCdiExtension;
import io.helidon.microprofile.graphql.server.test.db.TestDB;
import io.helidon.microprofile.graphql.server.test.enums.EnumTestWithNameAnnotation;
import io.helidon.microprofile.graphql.server.test.queries.NumberFormatQueriesAndMutations;
import io.helidon.microprofile.graphql.server.test.queries.SimpleQueriesWithArgs;
import io.helidon.microprofile.graphql.server.test.types.AbstractVehicle;
import io.helidon.microprofile.graphql.server.test.types.Car;
import io.helidon.microprofile.graphql.server.test.types.ContactRelationship;
import io.helidon.microprofile.graphql.server.test.types.SimpleContact;
import io.helidon.microprofile.graphql.server.test.types.SimpleContactWithNumberFormats;
import org.jboss.weld.junit5.WeldInitiator;
import org.jboss.weld.junit5.WeldJunit5Extension;
import org.jboss.weld.junit5.WeldSetup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(WeldJunit5Extension.class)
public class NumberFormatIT extends AbstractGraphQLIT {

    @WeldSetup
    private final WeldInitiator weld = WeldInitiator.of(WeldInitiator.createWeld()
                                                                .addBeanClass(SimpleContactWithNumberFormats.class)
                                                                .addBeanClass(NumberFormatQueriesAndMutations.class)
                                                                .addBeanClass(TestDB.class)
                                                                .addExtension(new GraphQLCdiExtension()));


    @Test
    public void testNumberFormats() throws IOException {
        setupIndex(indexFileName, SimpleContactWithNumberFormats.class, NumberFormatQueriesAndMutations.class);
        ExecutionContext executionContext =  new ExecutionContext(defaultContext);

        Map<String, Object> mapResults = getAndAssertResult(executionContext
                                                                    .execute("query { simpleFormattingQuery { id name age "
                                                                             + "bankBalance value longValue bigDecimal } }"));
        assertThat(mapResults.size(), is(1));

        Map<String, Object> mapResults2 = (Map<String, Object>) mapResults.get("simpleFormattingQuery");
        assertThat(mapResults2, is(notNullValue()));
        assertThat(mapResults2.get("id"), is("1 id"));
        assertThat(mapResults2.get("name"), is("Tim"));
        assertThat(mapResults2.get("age"), is("50 years old"));
        assertThat(mapResults2.get("bankBalance"), is("$ 1200.00"));
        assertThat(mapResults2.get("value"), is("10 value"));
        assertThat(mapResults2.get("longValue"), is(BigInteger.valueOf(Long.MAX_VALUE)));
        assertThat(mapResults2.get("bigDecimal"), is("BigDecimal-100"));

        mapResults = getAndAssertResult(executionContext.execute("mutation { generateDoubleValue }"));
        assertThat(mapResults, is(notNullValue()));
        assertThat(mapResults.get("generateDoubleValue"), is("Double-123456789"));

        mapResults = getAndAssertResult(executionContext.execute("query { echoBigDecimalUsingFormat(param1: \"BD-123\") }"));
        assertThat(mapResults, is(notNullValue()));
        assertThat(mapResults.get("echoBigDecimalUsingFormat"), is(BigDecimal.valueOf(123)));

        // COH-21891
        mapResults = getAndAssertResult(executionContext.execute("query { listAsString(arg1: [ [ \"value 12.12\", \"value 33.33\"] ] ) }"));
        assertThat(mapResults, is(notNullValue()));

        // create a new contact
//        String contactInput =
//                "contact: {"
//                        + "id: \"1 id\" "
//                        + "name: \"Tim\" "
//                        + "age: \"20 years old\" "
//                        + "bankBalance: \"$ 1000.01\" "
//                        + "value: \"9 value\" "
//                        + "longValue: 12345"
//                        + "bigDecimal: \"BigDecimal-12345\""
//                        + " } ";
//
//        mapResults = getAndAssertResult(
//                executionContext.execute("mutation { createSimpleContactWithNumberFormats (" + contactInput +
//                                                 ") { id name } }"));
//        assertThat(mapResults.size(), is(1));
//        mapResults2 = (Map<String, Object>) mapResults.get("createSimpleContactWithNumberFormats");
//        assertThat(mapResults2, is(notNullValue()));
    }

}
