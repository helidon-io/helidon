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
package io.helidon.data.jdbc.tests.declarative.repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import io.helidon.data.Data;
import io.helidon.data.jdbc.Jdbc;
import io.helidon.data.jdbc.tests.application.StoredOffsetDateTime;

/**
 * Declarative repository for real-driver scalar and typed-null binding coverage.
 */
@Data.Repository
@Data.Provider("jdbc")
public interface DeclarativeScalarBindingRepository {

    /**
     * Stores the local and offset components of an offset date-time separately.
     *
     * @param localDateTime local date-time component
     * @param offsetSeconds offset from UTC in seconds
     * @param id value identifier
     */
    @Jdbc.Statement("""
            UPDATE OFFSET_DATE_TIME_VALUE
            SET LOCAL_DATE_TIME = ?,
                OFFSET_SECONDS = ?
            WHERE ID = ?
            """)
    void updateTime(LocalDateTime localDateTime, int offsetSeconds, long id);

    /**
     * Reads the separately stored components of an offset date-time.
     *
     * @param id value identifier
     * @return portable stored representation
     */
    @Jdbc.Statement("""
            SELECT LOCAL_DATE_TIME AS localDateTime,
                   OFFSET_SECONDS AS offsetSeconds
            FROM OFFSET_DATE_TIME_VALUE
            WHERE ID = ?
            """)
    StoredOffsetDateTime findTime(long id);

    /**
     * Binds every supported reference scalar to a stored database column.
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
    @Jdbc.Statement("""
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
            """)
    @SuppressWarnings("checkstyle:ParameterNumber") // Exhaustive real-driver binding matrix.
    void bindAll(Boolean booleanValue,
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
                 Timestamp timestampValue);

    /**
     * Binds one named parameter to two physical positions.
     *
     * @param value repeated value
     */
    @Jdbc.Statement("""
            UPDATE SCALAR_VALUE
            SET STRING_VALUE = :value,
                SECOND_STRING_VALUE = :value
            WHERE ID = 1
            """)
    void bindRepeated(String value);

    /**
     * Binds two positional parameters in declaration order.
     *
     * @param first first stored value
     * @param second second stored value
     */
    @Jdbc.Statement("""
            UPDATE SCALAR_VALUE
            SET STRING_VALUE = ?,
                SECOND_STRING_VALUE = ?
            WHERE ID = 1
            """)
    void bindPositional(String first, String second);
}
