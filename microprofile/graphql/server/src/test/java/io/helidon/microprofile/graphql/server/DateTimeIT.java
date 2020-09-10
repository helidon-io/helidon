package io.helidon.microprofile.graphql.server;

import java.beans.IntrospectionException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import io.helidon.microprofile.graphql.server.AbstractGraphQLIT;
import io.helidon.microprofile.graphql.server.ExecutionContext;
import io.helidon.microprofile.graphql.server.GraphQLCdiExtension;
import io.helidon.microprofile.graphql.server.Schema;
import io.helidon.microprofile.graphql.server.SchemaFieldDefinition;
import io.helidon.microprofile.graphql.server.SchemaGenerator;
import io.helidon.microprofile.graphql.server.SchemaType;
import io.helidon.microprofile.graphql.server.test.db.TestDB;
import io.helidon.microprofile.graphql.server.test.queries.SimpleQueriesNoArgs;
import io.helidon.microprofile.graphql.server.test.types.DateTimePojo;
import io.helidon.microprofile.graphql.server.test.types.Person;
import org.jboss.weld.junit5.WeldInitiator;
import org.jboss.weld.junit5.WeldJunit5Extension;
import org.jboss.weld.junit5.WeldSetup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(WeldJunit5Extension.class)
public class DateTimeIT extends AbstractGraphQLIT {

    @WeldSetup
    private final WeldInitiator weld = WeldInitiator.of(WeldInitiator.createWeld()
                                                                .addBeanClass(SimpleQueriesNoArgs.class)
                                                                .addBeanClass(DateTimePojo.class)
                                                                .addBeanClass(TestDB.class)
                                                                .addExtension(new GraphQLCdiExtension()));

 @Test
    public void testDateAndTime() throws IOException {
        setupIndex(indexFileName, DateTimePojo.class, SimpleQueriesNoArgs.class);
        ExecutionContext executionContext =  new ExecutionContext(defaultContext);

        Schema schema = executionContext.getSchema();
        SchemaType type = schema.getTypeByName("DateTimePojo");

        SchemaFieldDefinition fd = getFieldDefinition(type, "localDate");
        assertThat(fd, is(notNullValue()));
        assertThat(fd.getFormat()[0], is("MM/dd/yyyy"));
        assertThat(fd.getDescription(), is(nullValue()));

        fd = getFieldDefinition(type, "localTime");
        assertThat(fd, is(notNullValue()));
        assertThat(fd.getFormat()[0], is("hh:mm:ss"));
        assertThat(fd.getDescription(), is(nullValue()));

        fd = getFieldDefinition(type, "localDate2");
        assertThat(fd, is(notNullValue()));
        assertThat(fd.getFormat()[0], is("MM/dd/yyyy"));
        assertThat(fd.getDescription(), is(nullValue()));

        // test default values for date and time
        assertDefaultFormat(type, "offsetTime", "HH:mm:ssZ");
        assertDefaultFormat(type, "localTime", "hh:mm:ss");
        assertDefaultFormat(type, "localDateTime", "yyyy-MM-dd'T'HH:mm:ss");
        assertDefaultFormat(type, "offsetDateTime", "yyyy-MM-dd'T'HH:mm:ssZ");
        assertDefaultFormat(type, "zonedDateTime", "yyyy-MM-dd'T'HH:mm:ssZ'['VV']'");
        assertDefaultFormat(type, "localDateNoFormat", "yyyy-MM-dd");
        assertDefaultFormat(type, "significantDates", "yyyy-MM-dd");

        fd = getFieldDefinition(type, "localDateTime");
        assertThat(fd, is(notNullValue()));
        assertThat(fd.getDescription(), is(nullValue()));
        assertThat(fd.getFormat()[0], is("yyyy-MM-dd'T'HH:mm:ss"));

        Map<String, Object> mapResults = getAndAssertResult(
                executionContext.execute("query { dateAndTimePOJOQuery { offsetDateTime offsetTime zonedDateTime "
                                             + "localDate localDate2 localTime localDateTime significantDates } }"));
        assertThat(mapResults.size(), is(1));
        Map<String, Object> mapResults2 = (Map<String, Object>) mapResults.get("dateAndTimePOJOQuery");
        assertThat(mapResults2, is(notNullValue()));
        assertThat(mapResults2.size(), is(8));

        assertThat(mapResults2.get("localDate"), is("02/17/1968"));
        assertThat(mapResults2.get("localDate2"), is("08/04/1970"));
        assertThat(mapResults2.get("localTime"), is("10:10:20"));
        assertThat(mapResults2.get("offsetTime"), is("08:10:01+0000"));
        Object significantDates = mapResults2.get("significantDates");
        assertThat(significantDates, is(notNullValue()));
        List<String> listDates = (ArrayList<String>) mapResults2.get("significantDates");
        assertThat(listDates.size(),is(2));
        assertThat(listDates.get(0), is("1968-02-17"));
        assertThat(listDates.get(1), is("1970-08-04"));

        mapResults = getAndAssertResult(
                executionContext.execute("query { localDateListFormat }"));
        assertThat(mapResults, is(notNullValue()));
        listDates = (ArrayList<String>) mapResults.get("localDateListFormat");
        assertThat(listDates.size(),is(2));
        assertThat(listDates.get(0), is("17/02/1968"));
        assertThat(listDates.get(1), is("04/08/1970"));
    }

}
