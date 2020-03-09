/*
 * Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
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
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import javax.enterprise.context.ApplicationScoped;

import io.helidon.microprofile.graphql.server.test.types.Address;
import io.helidon.microprofile.graphql.server.test.types.Person;

/**
 * An injectable datasource for integration tests.
 */
@ApplicationScoped
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
    public static final int MAX_PEOPLE = 100;

    private final Map<Integer, Person> allPeople = new HashMap<>();

    public TestDB() {
        for (int i = 1; i <= MAX_PEOPLE; i++) {
            Address homeAddress = generateHomeAddress();
            Address workAddress = generateWorkAddress();
            Address prevAddress1 = generateWorkAddress();
            Address prevAddress2 = generateWorkAddress();
            Person person = new Person(i, "Person " + i, homeAddress, workAddress, new BigDecimal(RANDOM.nextFloat()),
                                       List.of("BA", "BA Hon"),
                                       List.of(prevAddress1, prevAddress2), new int[0], new String[0], EMPTY_MAP,
                                       LocalDate.now());
            allPeople.put(person.getPersonId(), person);
        }
    }

    public Person getPerson(int personId) {
        return allPeople.get(personId);
    }

    public Collection<Person> getAllPeople() {
        return allPeople.values();
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

    public static Address generateHomeAddress() {
        return new Address("1234 Railway Parade", null, getRandomName(),
                           getRandomState(), getRandomZip(), "US");
    }

    public static Address generateWorkAddress() {
        return new Address("8 Yawkey Way", null, getRandomName(),
                           getRandomState(), getRandomZip(), "US");
    }
}
