/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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
package io.helidon.data.jdbc.tests.declarative.h2;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.util.Arrays;
import java.util.List;

import io.helidon.data.jdbc.tests.database.H2Database;
import io.helidon.data.jdbc.tests.declarative.repository.DeclarativeScalarBindingRepository;
import io.helidon.data.jdbc.tests.support.TestConfigFactory;
import io.helidon.service.registry.ServiceRegistryManager;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

class DeclarativeScalarBindingH2Test {
    private ServiceRegistryManager manager;
    private DeclarativeScalarBindingRepository repository;

    @BeforeEach
    void setUp() {
        TestConfigFactory.config(H2Database.config());
        manager = ServiceRegistryManager.start();
        repository = manager.registry().get(DeclarativeScalarBindingRepository.class);
    }

    /**
     * Proves generated binding supplies every supported reference scalar and JDBC-typed null to a real H2 statement.
     */
    @Test
    void bindsEveryReferenceScalarAndTypedNullThroughTheRealDriver() throws Exception {
        Boolean booleanValue = Boolean.TRUE;
        Byte byteValue = (byte) 2;
        Short shortValue = (short) 3;
        Integer integerValue = 4;
        Long longValue = 5L;
        Float floatValue = 6.5F;
        Double doubleValue = 7.5D;
        BigDecimal decimalValue = new BigDecimal("8.50");
        String stringValue = "value";
        byte[] bytesValue = {9, 10};
        LocalDate localDateValue = LocalDate.of(2026, 7, 27);
        LocalTime localTimeValue = LocalTime.of(10, 11, 12);
        LocalDateTime localDateTimeValue = LocalDateTime.of(2026, 7, 27, 10, 11, 12);
        OffsetTime offsetTimeValue = OffsetTime.parse("10:11:12+05:30");
        OffsetDateTime offsetDateTimeValue = OffsetDateTime.parse("2026-07-27T10:11:12+05:30");
        Date dateValue = Date.valueOf("2026-07-27");
        Time timeValue = Time.valueOf("10:11:12");
        Timestamp timestampValue = Timestamp.valueOf("2026-07-27 10:11:12");

        repository.bindAll(booleanValue,
                           byteValue,
                           shortValue,
                           integerValue,
                           longValue,
                           floatValue,
                           doubleValue,
                           decimalValue,
                           stringValue,
                           bytesValue,
                           localDateValue,
                           localTimeValue,
                           localDateTimeValue,
                           offsetTimeValue,
                           offsetDateTimeValue,
                           dateValue,
                           timeValue,
                           timestampValue);

        try (var connection = DriverManager.getConnection(H2Database.URL);
             var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT * FROM SCALAR_VALUE WHERE ID = 1")) {
            assertThat(result.next(), is(true));
            assertThat(result.getObject("BOOLEAN_VALUE", Boolean.class), is(booleanValue));
            assertThat(result.getObject("BYTE_VALUE", Byte.class), is(byteValue));
            assertThat(result.getObject("SHORT_VALUE", Short.class), is(shortValue));
            assertThat(result.getObject("INTEGER_VALUE", Integer.class), is(integerValue));
            assertThat(result.getObject("LONG_VALUE", Long.class), is(longValue));
            assertThat(result.getObject("FLOAT_VALUE", Float.class), is(floatValue));
            assertThat(result.getObject("DOUBLE_VALUE", Double.class), is(doubleValue));
            assertThat(result.getObject("DECIMAL_VALUE", BigDecimal.class), is(decimalValue));
            assertThat(result.getObject("STRING_VALUE", String.class), is(stringValue));
            assertThat(result.getBytes("BYTES_VALUE"), is(bytesValue));
            assertThat(result.getObject("LOCAL_DATE_VALUE", LocalDate.class), is(localDateValue));
            assertThat(result.getObject("LOCAL_TIME_VALUE", LocalTime.class), is(localTimeValue));
            assertThat(result.getObject("LOCAL_DATE_TIME_VALUE", LocalDateTime.class), is(localDateTimeValue));
            assertThat(result.getObject("OFFSET_TIME_VALUE", OffsetTime.class), is(offsetTimeValue));
            assertThat(result.getObject("OFFSET_DATE_TIME_VALUE", OffsetDateTime.class), is(offsetDateTimeValue));
            assertThat(result.getObject("DATE_VALUE", Date.class), is(dateValue));
            assertThat(result.getObject("TIME_VALUE", Time.class), is(timeValue));
            assertThat(result.getObject("TIMESTAMP_VALUE", Timestamp.class), is(timestampValue));
            assertThat(result.next(), is(false));
        }

        repository.bindAll(null,
                           null,
                           null,
                           null,
                           null,
                           null,
                           null,
                           null,
                           null,
                           null,
                           null,
                           null,
                           null,
                           null,
                           null,
                           null,
                           null,
                           null);

        try (var connection = DriverManager.getConnection(H2Database.URL);
             var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT * FROM SCALAR_VALUE WHERE ID = 1")) {
            assertThat(result.next(), is(true));
            for (String column : List.of("BOOLEAN_VALUE",
                                         "BYTE_VALUE",
                                         "SHORT_VALUE",
                                         "INTEGER_VALUE",
                                         "LONG_VALUE",
                                         "FLOAT_VALUE",
                                         "DOUBLE_VALUE",
                                         "DECIMAL_VALUE",
                                         "STRING_VALUE",
                                         "BYTES_VALUE",
                                         "LOCAL_DATE_VALUE",
                                         "LOCAL_TIME_VALUE",
                                         "LOCAL_DATE_TIME_VALUE",
                                         "OFFSET_TIME_VALUE",
                                         "OFFSET_DATE_TIME_VALUE",
                                         "DATE_VALUE",
                                         "TIME_VALUE",
                                         "TIMESTAMP_VALUE")) {
                assertThat(column, result.getObject(column), nullValue());
            }
            assertThat(result.next(), is(false));
        }
    }

    /**
     * Proves repeated named null markers and positional null markers bind every physical placeholder independently.
     */
    @Test
    void storesRepeatedNamedAndPositionalNullsAtEveryPhysicalPosition() throws Exception {
        repository.bindRepeated("repeated");
        assertThat(readStrings(), is(List.of("repeated", "repeated")));

        repository.bindRepeated(null);
        assertThat(readStrings(), is(Arrays.asList(null, null)));

        repository.bindPositional("first", "second");
        assertThat(readStrings(), is(List.of("first", "second")));

        repository.bindPositional(null, null);
        assertThat(readStrings(), is(Arrays.asList(null, null)));
    }

    @AfterEach
    void shutDown() {
        manager.shutdown();
    }

    private static List<String> readStrings() throws Exception {
        try (var connection = DriverManager.getConnection(H2Database.URL);
             var statement = connection.createStatement();
             var result = statement.executeQuery("""
                     SELECT STRING_VALUE, SECOND_STRING_VALUE
                     FROM SCALAR_VALUE
                     WHERE ID = 1
                     """)) {
            assertThat(result.next(), is(true));
            List<String> values = Arrays.asList(result.getString(1), result.getString(2));
            assertThat(result.next(), is(false));
            return values;
        }
    }
}
