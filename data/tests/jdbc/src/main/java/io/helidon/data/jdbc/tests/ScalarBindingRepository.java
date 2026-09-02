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
package io.helidon.data.jdbc.tests;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import io.helidon.data.Data;
import io.helidon.data.jdbc.Jdbc;

/**
 * Compile-time fixture for the complete declarative scalar binding matrix.
 */
@Data.Repository
@Data.Provider("jdbc")
public interface ScalarBindingRepository {

    /**
     * Binds every supported reference scalar to one physical JDBC marker.
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
            """)
    @SuppressWarnings("checkstyle:ParameterNumber") // Exhaustive generated-binding fixture for every reference scalar.
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
}
