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
import java.util.Optional;

/**
 * Parses strings into Java types according to the the datatypes and formats
 * supported by OpenAPI.
 * <p>
 * All methods that actually convert text to data (that is, methods other than
 * {@code converterMethod}) can throw an {@code IllegalArgumentException} if
 * the supplied value cannot be converted.
 */
public interface ValueConverter {

    /**
     * Reusable instance of the @{code ValueConverter}.
     */
    ValueConverter INSTANCE = new ValueConverterImpl();

    /**
     * Parses an {@code int}.
     *
     * @param value String to be parsed
     * @return {@code Optional} of the resulting {@code Integer}
     */
    Optional<Integer> toInteger(String value);

    Optional<Integer> toInteger(Optional<String> value);

    /**
     * Parses an {@code int} specified with 32-bit format.
     *
     * @param value String to be parsed
     * @return {@code Optional} of the resulting {@code Integer}
     */
    Optional<Integer> toIntegerInt32(String value);

    Optional<Integer> toIntegerInt32(Optional<String> value);

    /**
     * Parses a {@code long}.
     *
     * @param value String to be parsed
     * @return {@code Optional} of the resulting {@code Long}
     */
    Optional<Long> toIntegerInt64(String value);

    Optional<Long> toIntegerInt64(Optional<String> value);

    /**
     * Parses a (@code long}.
     *
     * @param value String to be parsed
     * @return {@code Optional} of the resulting {@code Long}
     */
    Optional<Long> toLong(String value);

    Optional<Long> toLong(Optional<String> value);

    /**
     * Parses a number as the default {@code double}.
     *
     * @param value String to be parsed
     * @return {@code Optional} of the resulting {@code Double}
     */
    Optional<Double> toNumber(String value);

    Optional<Double> toNumber(Optional<String> value);

    /**
     * Parses a {@code float}.
     *
     * @param value String to be parsed
     * @return {@code Optional} of the resulting {@code Float}
     */
    Optional<Float> toNumberFloat(String value);

    Optional<Float> toNumberFloat(Optional<String> value);

    /**
     * Parses a number specified as a {@code double}.
     *
     * @param value String to be parsed
     * @return {@code Optional} of the resulting {@code Double}
     */
    Optional<Double> parseNumberDouble(String value);

    Optional<Double> parseNumberDouble(Optional<String> value);

    /**
     * Parses a double.
     *
     * @param value String to be parsed
     * @return {@code Optional} of the resulting {@code Double}
     */
    Optional<Double> toDouble(String value);

    Optional<Double> toDouble(Optional<String> value);

    /**
     * Parses as a default {@code String}.
     *
     * @param value String to be parsed
     * @return {@code Optional} of the {@code String} itself
     */
    Optional<String> toString(String value);

    Optional<String> toString(Optional<String> value);

    /**
     * Parses a base64-encoded string as a {@code byte[]}.
     * @param value String to be parsed
     * @return {@code Optional} of the resulting {@code byte[]}
     */
    Optional<byte[]> toStringByte(String value);

    Optional<byte[]> toStringByte(Optional<String> value);

    /**
     * Parses a hex-encoded string as a {@code byte[]}.
     *
     * @param value String to be parsed
     * @return {@code Optional} of the resulting {@code byte[]}
     */
    Optional<byte[]> toStringBinary(String value);

    Optional<byte[]> toStringBinary(Optional<String> value);

    /**
     * Parses a {@code boolean}.
     *
     * @param value String to be parsed
     * @return {@code Optional} of the resulting {@code Boolean}
     */
    Optional<Boolean> toBoolean(String value);

    Optional<Boolean> toBoolean(Optional<String> value);

    /**
     * Parses a date in the RFC3339 full-date format as a {@code Date}.
     * <p>
     * Format is {@code date-fullyear "-" date-month "-" date-mday}.
     * @param value {@code Optional} of {@code String} to be parsed
     * @return the resulting {@code Date}
     */
    Optional<Date> toStringDate(String value);

    Optional<Date> toStringDate(Optional<String> value);

    /**
     * Parses a date in the RFC3339 date-time format as a {@code Date}.
     * <p>
     * Format is {@code full-date "T" full-time}
     *
     * @param value String to be parsed
     * @return {@code Optional} of the resulting {@code Date}
     */
    Optional<Date> toStringDateDateTime(String value);

    Optional<Date> toStringDateDateTime(Optional<String> value);

    /**
     * Parses a password.
     *
     * @param value String to be parsed
     * @return {@code Optional} of the resulting character array
     */
    Optional<char[]> toStringPassword(String value);

    Optional<char[]> toStringPassword(Optional<String> value);

}
