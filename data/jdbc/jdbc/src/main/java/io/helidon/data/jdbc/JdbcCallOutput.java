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
package io.helidon.data.jdbc;

import java.math.BigDecimal;
import java.sql.JDBCType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

record JdbcCallOutput(String name, int index, JDBCType type, String typeName, int scale) {
    static Class<?> canonical(JDBCType type) {
        return switch (type) {
        case BOOLEAN, BIT -> Boolean.class;
        case TINYINT -> Byte.class;
        case SMALLINT -> Short.class;
        case INTEGER -> Integer.class;
        case BIGINT -> Long.class;
        case REAL -> Float.class;
        case FLOAT, DOUBLE -> Double.class;
        case NUMERIC, DECIMAL -> BigDecimal.class;
        case CHAR, VARCHAR, LONGVARCHAR, NCHAR, NVARCHAR, LONGNVARCHAR -> String.class;
        case BINARY, VARBINARY, LONGVARBINARY -> byte[].class;
        case DATE -> LocalDate.class;
        case TIME -> LocalTime.class;
        case TIMESTAMP -> LocalDateTime.class;
        default -> null;
        };
    }
}
