/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

package io.helidon.microprofile.graphql.server.test.db;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import io.helidon.microprofile.graphql.server.test.types.DateTimePojo;
import io.helidon.microprofile.graphql.server.test.types.TypeWithNameAndJsonbProperty;
import javax.enterprise.context.ApplicationScoped;

import io.helidon.microprofile.graphql.server.test.types.Address;
import io.helidon.microprofile.graphql.server.test.types.DefaultValuePOJO;
import io.helidon.microprofile.graphql.server.test.types.MultiLevelListsAndArrays;
import io.helidon.microprofile.graphql.server.test.types.Person;
import io.helidon.microprofile.graphql.server.test.types.SimpleContact;

/**
 * An injectable datasource for integration tests.
 */
@ApplicationScoped
@SuppressWarnings("unchecked")
public class TestDB {

    /**
     * An empty map.
     */
    protected static final Map EMPTY_MAP = new HashMap<>();

    /**
     * US Postal Service two letter postal codes.
     */
    private static final String[] STATE_CODES = {
            "AL", "AK", "AS", "AZ", "AR", "CA", "CO", "CT", "DE", "OF", "DC",
            "FM", "FL", "GA", "GU", "HI", "ID", "IL", "IN", "IA", "KS", "KY",
            "LA", "ME", "MH", "MD", "MA", "MI", "MN", "MS", "MO", "MT", "NE",
            "NV", "NH", "NJ", "NM", "NY", "NC", "ND", "MP", "OH", "OK", "OR",
            "PW", "PA", "PR", "RI", "SC", "SD", "TN", "TX", "UT", "VT", "VI",
            "VA", "WA", "WV", "WI", "WY"
    };

    /**
     * Used for generating random dates.
     */
    private static Random RANDOM = new Random();

    /**
     * Maximum number people to create.
     */
    public static final int MAX_PEOPLE = 1000;

    private final Map<Integer, Person> allPeople = new HashMap<>();

    public TestDB() {
        for (int i = 1; i <= MAX_PEOPLE; i++) {
            Person p = generatePerson(i);
            allPeople.put(p.getPersonId(), p);

        }
    }

    /**
     * Generate a random {@link Person}.
     *
     * @param personId person id to use
     * @return a random {@link Person}
     */
    public Person generatePerson(int personId) {
        Address homeAddress = generateHomeAddress();
        Address workAddress = generateWorkAddress();
        Address prevAddress1 = generateWorkAddress();
        Address prevAddress2 = generateWorkAddress();
        return new Person(personId, "Person " + personId, homeAddress, workAddress, BigDecimal.valueOf(RANDOM.nextFloat()),
                          List.of("BA", "BA Hon"),
                          List.of(prevAddress1, prevAddress2), new int[0], new String[0], EMPTY_MAP,
                          LocalDate.now(), System.nanoTime(), BigDecimal.valueOf(10));
    }

    public Person getPerson(int personId) {
        return allPeople.get(personId);
    }

    public Collection<Person> getAllPeople() {
        return allPeople.values();
    }

    public MultiLevelListsAndArrays getMultiLevelListsAndArrays() {
        List<List<BigDecimal>> listListBigDecimal = new ArrayList<>();
        listListBigDecimal.add(Collections.singletonList(new BigDecimal(100)));
        int[][] intMultiLevelArray = new int[2][];
        intMultiLevelArray[0] = new int[] { 1, 2, 3 };
        intMultiLevelArray[1] = new int[] { 4, 5, 6 };
        Person[][] personMultiLevelArray = new Person[3][];
        personMultiLevelArray[0] = new Person[] { generatePerson(1), generatePerson(2) };
        personMultiLevelArray[1] = new Person[] { generatePerson(3), generatePerson(4) };
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

    public SimpleContact createRandomContact() {
        return new SimpleContact(UUID.randomUUID().toString(), "Name-" + RANDOM.nextInt(10000), RANDOM.nextInt(100) + 1);
    }

    public SimpleContact createContactWithName(String name) {
        return new SimpleContact(UUID.randomUUID().toString(), name, RANDOM.nextInt(100) + 1);
    }

    /**
     * Return a random Zip code.
     *
     * @return a random Zip code
     */
    private static String getRandomZip() {
        return String.valueOf(RANDOM.nextInt(99999));
    }

    /**
     * Return a random state.
     *
     * @return a random state
     */
    private static String getRandomState() {
        return STATE_CODES[RANDOM.nextInt(STATE_CODES.length)];
    }

    /**
     * Return a random name.
     *
     * @return a random name
     */
    private static String getRandomName() {
        int cCh = 4 + RANDOM.nextInt(7);
        char[] ach = new char[cCh];

        ach[0] = (char) ('A' + RANDOM.nextInt(26));
        for (int of = 1; of < cCh; ++of) {
            ach[of] = (char) ('a' + RANDOM.nextInt(26));
        }
        return new String(ach);
    }

    public Address generateHomeAddress() {
        return new Address("1234 Railway Parade", null, getRandomName(),
                           getRandomState(), getRandomZip(), "US");
    }

    public Address generateWorkAddress() {
        return new Address("8 Yawkey Way", null, getRandomName(),
                           getRandomState(), getRandomZip(), "US");
    }

    public DefaultValuePOJO generatePOJO(String id, int value) {
        return new DefaultValuePOJO(id, value);
    }

    public TypeWithNameAndJsonbProperty getTypeWithNameAndJsonbProperty() {
        return new TypeWithNameAndJsonbProperty("name1", "name2", "name3", "name4", "name5", "name6");
    }

    public DateTimePojo getDateTimePOJO() {
        return new DateTimePojo(LocalDate.of(1968, 2, 17),
                                LocalDate.of(1970, 8, 4),
                                LocalTime.of(10, 10, 20),
                                OffsetTime.of(8, 10, 1, 0, ZoneOffset.UTC),
                                LocalDateTime.now(), OffsetDateTime.now(), ZonedDateTime.now(), LocalDate.now(),
                                List.of(LocalDate.of(1968, 2, 17), LocalDate.of(1970, 8, 4)),
                                List.of(LocalDate.of(1968, 2, 17), LocalDate.of(1970, 8, 4)));
    }
}
