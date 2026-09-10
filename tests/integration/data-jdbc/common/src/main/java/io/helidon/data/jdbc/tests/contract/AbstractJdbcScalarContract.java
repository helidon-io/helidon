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
package io.helidon.data.jdbc.tests.contract;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;

import io.helidon.data.Data;
import io.helidon.data.jdbc.JdbcClient;
import io.helidon.service.registry.Qualifier;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.ServiceRegistryManager;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.number.OrderingComparison.comparesEqualTo;
import static org.junit.jupiter.api.Assertions.assertAll;

/**
 * Real-driver contract for every scalar supported by the imperative JDBC client.
 */
@SuppressWarnings("helidon:api:preview")
public abstract class AbstractJdbcScalarContract {
    private static final String UPDATE_SQL = """
            UPDATE SCALAR_VALUE
            SET BOOLEAN_VALUE = ?,
                BYTE_VALUE = ?,
                SHORT_VALUE = ?,
                INTEGER_VALUE = ?,
                LONG_VALUE = ?,
                FLOAT_VALUE = ?,
                DOUBLE_VALUE = ?,
                DECIMAL_VALUE = ?,
                STRING_VALUE = ?,
                BYTES_VALUE = ?,
                LOCAL_DATE_VALUE = ?,
                LOCAL_TIME_VALUE = ?,
                LOCAL_DATE_TIME_VALUE = ?,
                DATE_VALUE = ?,
                TIME_VALUE = ?,
                TIMESTAMP_VALUE = ?
            WHERE ID = 1
            """;
    private static final String SELECT_SQL = """
            SELECT BOOLEAN_VALUE,
                   BYTE_VALUE,
                   SHORT_VALUE,
                   INTEGER_VALUE,
                   LONG_VALUE,
                   FLOAT_VALUE,
                   DOUBLE_VALUE,
                   DECIMAL_VALUE,
                   STRING_VALUE,
                   BYTES_VALUE,
                   LOCAL_DATE_VALUE,
                   LOCAL_TIME_VALUE,
                   LOCAL_DATE_TIME_VALUE,
                   DATE_VALUE,
                   TIME_VALUE,
                   TIMESTAMP_VALUE
            FROM SCALAR_VALUE
            WHERE ID = 1
            """;
    private static final String SELECT_PORTABLE_CONVERSIONS_SQL = """
            SELECT INTEGER_VALUE,
                   INTEGER_VALUE
            FROM SCALAR_VALUE
            WHERE ID = 1
            """;
    private static final ScalarValues VALUES = new ScalarValues(Boolean.TRUE,
                                                                 (byte) 2,
                                                                 (short) 3,
                                                                 4,
                                                                 5L,
                                                                 6.5F,
                                                                 7.5D,
                                                                 new BigDecimal("8.50"),
                                                                 "value",
                                                                 new byte[] {9, 10},
                                                                 LocalDate.of(2026, 7, 27),
                                                                 LocalTime.of(10, 11, 12),
                                                                 LocalDateTime.of(2026, 7, 27, 10, 11, 12),
                                                                 Date.valueOf("2026-07-27"),
                                                                 Time.valueOf("10:11:12"),
                                                                 Timestamp.valueOf("2026-07-27 10:11:12"));
    private static final ScalarValues NULL_VALUES = new ScalarValues(null,
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

    private ServiceRegistryManager manager;
    private JdbcClient client;

    /**
     * Allows a database-specific leaf test to publish dynamic configuration before the registry starts.
     */
    protected abstract void beforeStartApplication();

    /**
     * Returns the registry-managed JDBC client used by this contract.
     *
     * @return JDBC client
     */
    protected final JdbcClient client() {
        return client;
    }

    /**
     * Resolves an application service from the active test registry.
     *
     * @param serviceType service type
     * @param <T> service type
     * @return resolved service
     */
    protected final <T> T service(Class<T> serviceType) {
        return manager.registry().get(serviceType);
    }

    /**
     * Binds the scalar values through the operation under test.
     *
     * @param values scalar values
     */
    protected void bindAll(ScalarValues values) {
        client.create(UPDATE_SQL)
                .bind(1, values.booleanValue())
                .bind(2, values.byteValue())
                .bind(3, values.shortValue())
                .bind(4, values.integerValue())
                .bind(5, values.longValue())
                .bind(6, values.floatValue())
                .bind(7, values.doubleValue())
                .bind(8, values.decimalValue())
                .bind(9, values.stringValue())
                .bind(10, values.bytesValue())
                .bind(11, values.localDateValue())
                .bind(12, values.localTimeValue())
                .bind(13, values.localDateTimeValue())
                .bind(14, values.dateValue())
                .bind(15, values.timeValue())
                .bind(16, values.timestampValue())
                .execute();
    }

    /**
     * Returns the all-null scalar value set used by generated typed-null tests.
     *
     * @return all-null values
     */
    protected final ScalarValues nullValues() {
        return NULL_VALUES;
    }

    /**
     * Verifies every stored column is SQL {@code NULL} through Helidon's typed row accessors.
     */
    protected final void assertAllNull() {
        client.create(SELECT_SQL)
                .map(row -> {
                    assertAll("typed SQL null scalar mappings",
                              () -> assertThat(row.optional(1, Boolean.class), is(Optional.empty())),
                              () -> assertThat(row.optional(2, Byte.class), is(Optional.empty())),
                              () -> assertThat(row.optional(3, Short.class), is(Optional.empty())),
                              () -> assertThat(row.optional(4, Integer.class), is(Optional.empty())),
                              () -> assertThat(row.optional(5, Long.class), is(Optional.empty())),
                              () -> assertThat(row.optional(6, Float.class), is(Optional.empty())),
                              () -> assertThat(row.optional(7, Double.class), is(Optional.empty())),
                              () -> assertThat(row.optional(8, BigDecimal.class), is(Optional.empty())),
                              () -> assertThat(row.optional(9, String.class), is(Optional.empty())),
                              () -> assertThat(row.optional(10, byte[].class), is(Optional.empty())),
                              () -> assertThat(row.optional(11, LocalDate.class), is(Optional.empty())),
                              () -> assertThat(row.optional(12, LocalTime.class), is(Optional.empty())),
                              () -> assertThat(row.optional(13, LocalDateTime.class), is(Optional.empty())),
                              () -> assertThat(row.optional(14, Date.class), is(Optional.empty())),
                              () -> assertThat(row.optional(15, Time.class), is(Optional.empty())),
                              () -> assertThat(row.optional(16, Timestamp.class), is(Optional.empty())));
                    return true;
                })
                .one();
    }

    @BeforeEach
    protected final void setUpScalarContract() {
        beforeStartApplication();
        manager = ServiceRegistryManager.start();
        Qualifier provider = Qualifier.builder()
                .typeName(Data.ProviderType.TYPE)
                .value("jdbc")
                .build();
        client = manager.registry().get(JdbcClient.class,
                                        Qualifier.createNamed(Service.Named.DEFAULT_NAME),
                                        provider);
    }

    /**
     * Proves every supported non-null scalar binds and maps through the real JDBC driver.
     */
    @Test
    protected final void bindsAndMapsEverySupportedScalarThroughTheRealDriver() {
        bindAll(VALUES);

        client.create(SELECT_SQL)
                .map(row -> {
                    assertAll("non-null scalar mappings",
                              () -> assertThat(row.get(1, Boolean.class), is(VALUES.booleanValue())),
                              () -> assertThat(row.get(2, Byte.class), is(VALUES.byteValue())),
                              () -> assertThat(row.get(3, Short.class), is(VALUES.shortValue())),
                              () -> assertThat(row.get(4, Integer.class), is(VALUES.integerValue())),
                              () -> assertThat(row.get(5, Long.class), is(VALUES.longValue())),
                              () -> assertThat(row.get(6, Float.class), is(VALUES.floatValue())),
                              () -> assertThat(row.get(7, Double.class), is(VALUES.doubleValue())),
                              () -> assertThat(row.get(8, BigDecimal.class),
                                               comparesEqualTo(VALUES.decimalValue())),
                              () -> assertThat(row.get(9, String.class), is(VALUES.stringValue())),
                              () -> assertThat(row.get(10, byte[].class), is(VALUES.bytesValue())),
                              () -> assertThat(row.get(11, LocalDate.class), is(VALUES.localDateValue())),
                              () -> assertThat(row.get(12, LocalTime.class), is(VALUES.localTimeValue())),
                              () -> assertThat(row.get(13, LocalDateTime.class), is(VALUES.localDateTimeValue())),
                              () -> assertThat(row.get(14, Date.class), is(VALUES.dateValue())),
                              () -> assertThat(row.get(15, Time.class), is(VALUES.timeValue())),
                              () -> assertThat(row.get(16, Timestamp.class), is(VALUES.timestampValue())));
                    return true;
                })
                .one();
    }

    /**
     * Proves common JDBC numeric conversions use portable scalar getters through the real driver.
     */
    @Test
    protected final void mapsPortableNumericConversionsThroughTheRealDriver() {
        bindAll(VALUES);

        client.create(SELECT_PORTABLE_CONVERSIONS_SQL)
                .map(row -> {
                    assertAll("portable numeric scalar conversions",
                              () -> assertThat(row.get(1, Long.class), is(4L)),
                              () -> assertThat(row.get(2, BigDecimal.class),
                                               comparesEqualTo(BigDecimal.valueOf(4L))));
                    return true;
                })
                .one();
    }

    @AfterEach
    protected final void shutDownScalarContract() {
        if (manager != null) {
            manager.shutdown();
        }
    }

    /**
     * Complete set of supported reference scalar values.
     *
     * @param booleanValue boolean value
     * @param byteValue byte value
     * @param shortValue short value
     * @param integerValue integer value
     * @param longValue long value
     * @param floatValue float value
     * @param doubleValue double value
     * @param decimalValue decimal value
     * @param stringValue string value
     * @param bytesValue binary value
     * @param localDateValue local date value
     * @param localTimeValue local time value
     * @param localDateTimeValue local date-time value
     * @param dateValue JDBC date value
     * @param timeValue JDBC time value
     * @param timestampValue JDBC timestamp value
     */
    @SuppressWarnings("checkstyle:ParameterNumber")
    protected record ScalarValues(Boolean booleanValue,
                                  Byte byteValue,
                                  Short shortValue,
                                  Integer integerValue,
                                  Long longValue,
                                  Float floatValue,
                                  Double doubleValue,
                                  BigDecimal decimalValue,
                                  String stringValue,
                                  byte[] bytesValue,
                                  LocalDate localDateValue,
                                  LocalTime localTimeValue,
                                  LocalDateTime localDateTimeValue,
                                  Date dateValue,
                                  Time timeValue,
                                  Timestamp timestampValue) {
    }
}
