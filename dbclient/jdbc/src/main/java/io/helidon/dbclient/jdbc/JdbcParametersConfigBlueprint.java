/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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
package io.helidon.dbclient.jdbc;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

/**
 * JDBC parameters setter configuration.
 */
@Prototype.Blueprint
@Prototype.Configured(value = "parameters", root = false)
interface JdbcParametersConfigBlueprint {

    /**
     * Use SQL {@code NCHAR}, {@code NVARCHAR} or {@code LONGNVARCHAR} value conversion
     * for {@link String} values.
     * Default value is {@code false}.
     *
     * @return whether N{@link String} conversion is used
     */
    @Option.Configured
    @Option.DefaultBoolean(false)
    boolean useNString();

    /**
     * Use {@link java.sql.PreparedStatement#setCharacterStream(int, java.io.Reader, int)} binding
     * for {@link String} values with length above {@link #stringBindingSize()} limit.
     * Default value is {@code true}.
     *
     * @return whether to use {@link java.io.CharArrayReader} binding
     */
    @Option.Configured
    @Option.DefaultBoolean(true)
    boolean useStringBinding();

    /**
     * {@link String} values with length above this limit will be bound
     * using {@link java.sql.PreparedStatement#setCharacterStream(int, java.io.Reader, int)}
     * if {@link #useStringBinding()} is set to {@code true}.
     * Default value is {@code 1024}.
     *
     * @return {@link String} values length limit for {@link java.io.CharArrayReader} binding
     */
    @Option.Configured
    @Option.DefaultInt(1024)
    int stringBindingSize();

    /**
     * Use {@link java.sql.PreparedStatement#setBinaryStream(int, java.io.InputStream, int)} binding
     * for {@code byte[]} values.
     * Default value is {@code true}.
     *
     * @return whether to use {@link java.io.ByteArrayInputStream} binding
     */
    @Option.Configured
    @Option.DefaultBoolean(true)
    boolean useByteArrayBinding();

    /**
     * Use {@link java.sql.PreparedStatement#setTimestamp(int, java.sql.Timestamp)}
     * to set {@link java.time.LocalTime} values when {@code true}
     * or use {@link java.sql.PreparedStatement#setTime(int, java.sql.Time)} when {@code false}.
     * Default value is {@code true}.
     * <p>This option is vendor specific. Most of the databases are fine with {@link java.sql.Timestamp},
     * but for example SQL Server requires {@link java.sql.Time}.
     * This option does not apply when {@link #setObjectForJavaTime()} is set to {@code true}.
     *
     * @return whether to use {@link java.sql.Timestamp} instead of {@link java.sql.Time}
     *         for {@link java.time.LocalTime} values
     */
    @Option.Configured
    @Option.DefaultBoolean(true)
    boolean timestampForLocalTime();

    /**
     * Set all {@code java.time} Date/Time values directly using {@link java.sql.PreparedStatement#setObject(int, Object)}.
     * This option shall work fine for recent JDBC drivers.
     * Default value is {@code true}.
     *
     * @return whether to use {@link java.sql.PreparedStatement#setObject(int, Object)} for {@code java.time} Date/Time values
     */
    @Option.Configured
    @Option.DefaultBoolean(true)
    boolean setObjectForJavaTime();

}
