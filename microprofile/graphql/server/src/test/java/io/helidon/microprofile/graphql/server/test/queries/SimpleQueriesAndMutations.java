/*
 * Copyright (c) 2020, 2021 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.helidon.microprofile.graphql.server.test.queries;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.json.bind.annotation.JsonbDateFormat;
import javax.json.bind.annotation.JsonbProperty;

import io.helidon.microprofile.graphql.server.test.types.SimpleDateTime;
import io.helidon.microprofile.graphql.server.test.db.TestDB;
import io.helidon.microprofile.graphql.server.test.enums.EnumTestWithEnumName;
import io.helidon.microprofile.graphql.server.test.types.DateTimePojo;
import io.helidon.microprofile.graphql.server.test.types.MultiLevelListsAndArrays;
import io.helidon.microprofile.graphql.server.test.types.Person;
import io.helidon.microprofile.graphql.server.test.types.SimpleContactWithSelf;
import io.helidon.microprofile.graphql.server.test.types.TypeWithIDs;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.eclipse.microprofile.graphql.DateFormat;
import org.eclipse.microprofile.graphql.GraphQLApi;
import org.eclipse.microprofile.graphql.Id;
import org.eclipse.microprofile.graphql.Mutation;
import org.eclipse.microprofile.graphql.Name;
import org.eclipse.microprofile.graphql.Query;

/**
 * Class that holds simple query definitions with no-argument.
 */
@GraphQLApi
@ApplicationScoped
public class SimpleQueriesAndMutations {

    @Inject
    private TestDB testDB;

    public SimpleQueriesAndMutations() {
    }

    @Mutation
    public SimpleDateTime echoSimpleDateTime(@Name("value") SimpleDateTime simpleDateTime) {
        return simpleDateTime;
    }

    @Query
    public Boolean isBooleanObject() {
        return Boolean.valueOf(true);
    }

    @Query
    public boolean isBooleanPrimitive() {
        return false;
    }

    @Query
    @Name("idQuery")
    @Id
    public String returnId() {
        return "an-id";
    }

    @Query
    public String hero() {
        return "R2-D2";
    }

    @Query
    @Name("episodeCount")
    public int getNumberOfEpisodes() {
        return 9;
    }

    @Query
    @JsonbProperty("numberOfStars")
    public Long getTheNumberOfStars() {
        return Long.MAX_VALUE;
    }

    @Query("badGuy")
    public String getVillain() {
        return "Darth Vader";
    }

    @Query("allPeople")
    public Collection<Person> findAllPeople() {
        return testDB.getAllPeople();
    }

    @Query
    public LocalDate returnCurrentDate() {
        return LocalDate.now();
    }

    @Query
    @Name("returnMediumSize")
    public EnumTestWithEnumName getEnumMedium() {
        return EnumTestWithEnumName.M;
    }

    @Query
    public TypeWithIDs returnTypeWithIDs() {
        return new TypeWithIDs(1, 2, "string", 10L, 10L, UUID.randomUUID());
    }

    @Query
    public SimpleContactWithSelf returnSimpleContactWithSelf() {
        SimpleContactWithSelf spouse = new SimpleContactWithSelf("c1", "contact1", 30);
        SimpleContactWithSelf contact = new SimpleContactWithSelf("c2", "contact2", 33);
        contact.setSpouse(spouse);
        return contact;
    }

    @Query
    @Name("getMultiLevelList")
    public MultiLevelListsAndArrays returnLists() {
        List<List<BigDecimal>> listListBigDecimal = new ArrayList<>();
        listListBigDecimal.add(Collections.singletonList(new BigDecimal(100)));
        int[][] intMultiLevelArray = new int[2][];
        intMultiLevelArray[0] = new int[] { 1, 2, 3 };
        intMultiLevelArray[1] = new int[] { 4, 5, 6 };
        Person[][] personMultiLevelArray = new Person[3][];
        personMultiLevelArray[0] = new Person[] { testDB.generatePerson(1), testDB.generatePerson(2) };
        personMultiLevelArray[1] = new Person[] { testDB.generatePerson(3), testDB.generatePerson(4) };
        List<String[]> listOfStringArrays = new ArrayList<>();
        listOfStringArrays.add(new String[] { "one", "two", "three" });
        listOfStringArrays.add(new String[] { "four", "five" });
        String[][][] multiStringArray = { { { "one", "two" }, { "three", "four" } },
                { { "five", "six" }, { "seven", "eight" } } };
        Collection<Collection<Collection<String>>> colColColString = new ArrayList<>();
        colColColString.add(Collections.singletonList(Collections.singleton("a")));

        return new MultiLevelListsAndArrays(listListBigDecimal, null, null, intMultiLevelArray,
                                            personMultiLevelArray, listOfStringArrays, multiStringArray, colColColString);
    }

    @Query("dateAndTimePOJOQuery")
    public DateTimePojo dateTimePojo() {
        return testDB.getDateTimePOJO();
    }

    @Query("localDateListFormat")
    public List<@DateFormat("dd/MM/yyyy") LocalDate> getLocalDateListFormat() {
        return List.of(LocalDate.of(1968, 2, 17), LocalDate.of(1970, 8, 4));
    }

    @Query
    @DateFormat(value = "dd MMM yyyy", locale = "en-GB")
    public LocalDate transformedDate() {
        String date = "2016-08-16";
        return LocalDate.parse(date);
    }

    @Query
    public DateTimePojo echoDateTimePojo(@Name("value") DateTimePojo dateTimePojo) {
        return dateTimePojo;
    }

    @Query("localDateNoFormat")
    public LocalDate localDateNoFormat() {
        return LocalDate.of(1968, 02, 17);
    }

    @Mutation
    public DateTimePojo dateTimePojoMutation() {
        return testDB.getDateTimePOJO();
    }

    @Mutation
    public LocalDate echoLocalDate(@DateFormat("dd/MM/yyyy") @Name("dateArgument") LocalDate date) {
        return date;
    }

    @Mutation
    @DateFormat(value = "dd MMM yyyy", locale = "en-GB")
    public LocalDate echoLocalDateGB(@DateFormat(value = "dd/MM/yyyy") @Name("dateArgument") LocalDate date) {
        return date;
    }

    @Query
    @DateFormat(value = "dd MMM yyyy", locale = "en-GB")
    public LocalDate queryLocalDateGB() {
        return LocalDate.of(1968, 2, 17);
    }

    @Mutation
    @JsonbDateFormat("HH:mm:ss dd-MM-yyyy")
    public LocalDateTime testDefaultFormatLocalDateTime(@Name("dateTime") LocalDateTime localDateTime) {
        return localDateTime;
    }

    @Mutation
    @JsonbDateFormat("HH:mm")
    public LocalTime echoLocalTime(@Name("time") LocalTime localTime) {
        return localTime;
    }
    
    @Query
    @JsonbDateFormat("dd/MM")
    public List<LocalDate> echoFormattedLocalDateWithReturnFormat(@Name("value") List<@DateFormat("dd-MM-yyyy") LocalDate> value) {
        return value;
    }

    @Mutation
    @DateFormat("dd/MM/yyyy")
    public List<LocalDate> echoFormattedDateWithJsonB(@Name("dates")
                                                      @JsonbDateFormat("yy dd MM") // should be ignored
                                                      List<@DateFormat("MM/dd/yyyy") LocalDate> localDates) {
        return localDates;
    }

    @Query
    public OffsetDateTime echoOffsetDateTime(@Name("value")
                                             @JsonbDateFormat(value = "dd MMM yyyy 'at' HH:mm 'in zone' Z",locale = "en-ZA")
                                             OffsetDateTime offsetDateTime) {
        return offsetDateTime;
    }

    @Query
    public ZonedDateTime echoZonedDateTime(@Name("value")
                                           @JsonbDateFormat(value = "dd MMMM yyyy 'at' HH:mm 'in' VV",locale = "en-ZA")
                                           ZonedDateTime zonedDateTime) {
        return zonedDateTime;
    }

    @Query
    public Date echoLegacyDate(@Name("value") Date legacyDate) {
        return legacyDate;
    }
}
