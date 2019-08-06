/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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
 *
 */
package io.helidon.openapi;

import java.util.Date;

/**
 * Parses strings into Java types according to the the datatypes and formats
 * supported by OpenAPI.
 */
public interface ValueParser {

    /**
     * Reusable instance of the @{code ValueParser}.
     */
    ValueParser PARSER = new ValueParserImpl();

    /**
     * Returns the method name of the converter method
     * given the datatype to be converted. Primarily useful for code generation.
     *
     * @param datatype name of the datatype to be converted
     * @return name of the method that converts the datatype; null if none
     */
    String converterMethod(String datatype);

    /**
     * Returns the method name of the converter method for
     * the datatype and format specified. Primarily useful for code generation.
     * @param datatype datatype name to be converted
     * @param format format name to be converted
     * @return name of the method that converts the datatype/format
     */
    String converterMethod(String datatype, String format);

    /**
     * Parses an {@code int}.
     *
     * @param value String to be parsed
     * @return the resulting {@code int}
     */
    int integer(String value);

    /**
     * Parses an {@code int} specified with 32-bit format.
     *
     * @param value String to be parsed
     * @return the resulting {@code int}
     */
    int integerInt32(String value);

    /**
     * Parses a {@code long}.
     *
     * @param value String to be parsed
     * @return the resulting {@code long}
     */
    long integerInt64(String value);

    /**
     * Parses a number as the default {@code double}.
     *
     * @param value String to be parsed
     * @return the resulting {@code double}
     */
    double number(String value);

    /**
     * Parses a {@code float}.
     *
     * @param value String to be parsed
     * @return the resulting {@code float}
     */
    float numberFloat(String value);

    /**
     * Parses a number specified as a {@code double}.
     *
     * @param value String to be parsed
     * @return the resulting {@code double}
     */
    double numberDouble(String value);

    /**
     * Parses as a default {@code String}.
     *
     * @param value String to be parsed
     * @return the String itself
     */
    String string(String value);

    /**
     * Parses a base64-encoded string as a {@code byte[]}.
     * @param value String to be parsed
     * @return the resulting {@code byte[]}
     */
    byte[] stringByte(String value);

    /**
     * Parses a hex-encoded string as a {@code byte[]}.
     *
     * @param value String to be parsed
     * @return the resulting {@code byte[]}
     */
    byte[] stringBinary(String value);

    /**
     * Parses a {@code boolean}.
     *
     * @param value String to be parsed
     * @return the resulting {@code boolean}
     */
    boolean bool(String value);

    /**
     * Parses a date in the RFC3339 full-date format as a {@code Date}.
     * <p>
     * Format is {@code date-fullyear "-" date-month "-" date-mday}.
     * @param value String to be parsed
     * @return the resulting {@code Date}
     */
    Date date(String value);

    /**
     * Parses a date in the RFC3339 date-time format as a {@code Date}.
     * <p>
     * Format is {@code full-date "T" full-time}
     *
     * @param value String to be parsed
     * @return the resulting {@code Date}
     */
    // date-time       = full-date "T" full-time
    Date dateDateTime(String value);

    /**
     * Parses a password.
     *
     * @param value String to be parsed
     * @return the resulting character array
     */
    char[] stringPassword(String value);

}
