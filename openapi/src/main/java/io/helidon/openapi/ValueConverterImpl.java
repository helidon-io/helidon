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

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;
import java.util.Optional;

import javax.xml.bind.DatatypeConverter;

/**
 * Parses strings into OpenAPI datatypes.
 */
public class ValueConverterImpl implements ValueConverter {

    private static final String FULL_DATE_FORMAT_STRING = "yyyy-MM-dd";

    private static final SimpleDateFormat DATE_TIME_FORMAT =
            new SimpleDateFormat(FULL_DATE_FORMAT_STRING + "'T'HH:mm:ss.SSSX");

    private static final SimpleDateFormat FULL_DATE_FORMAT =
            new SimpleDateFormat(FULL_DATE_FORMAT_STRING);

    @Override
    public Optional<Integer> toInteger(String value) throws NumberFormatException {
        return toIntegerInt32(value);
    }

    @Override
    public Optional<Integer> toInteger(Optional<String> value) {
        return toInteger(value.orElse(null));
    }

    @Override
    public Optional<Integer> toIntegerInt32(String value) throws NumberFormatException {
        return Optional.ofNullable(value == null ? null : Integer.valueOf(value));
    }

    @Override
    public Optional<Integer> toIntegerInt32(Optional<String> value) {
        return toIntegerInt32(value.orElse(null));
    }

    @Override
    public Optional<Long> toIntegerInt64(String value) throws NumberFormatException {
        return toLong(value);
    }

    @Override
    public Optional<Long> toIntegerInt64(Optional<String> value) {
        return toIntegerInt64(value.orElse(null));
    }


    @Override
    public Optional<Long> toLong(String value) {
        return Optional.ofNullable(value == null ? null : Long.valueOf(value));
    }

    @Override
    public Optional<Long> toLong(Optional<String> value) {
        return toLong(value.orElse(null));
    }

    @Override
    public Optional<Double> toNumber(String value) throws NumberFormatException {
        return toDouble(value);
    }

    @Override
    public Optional<Double> toNumber(Optional<String> value) {
        return toNumber(value.orElse(null));
    }

    @Override
    public Optional<Double> toDouble(String value) {
        return Optional.ofNullable(value == null ? null : Double.valueOf(value));
    }

    @Override
    public Optional<Double> toDouble(Optional<String> value) {
        return toDouble(value.orElse(null));
    }

    @Override
    public Optional<Float> toNumberFloat(String value) throws NumberFormatException {
        return Optional.ofNullable(value == null ? null : Float.valueOf(value));
    }

    @Override
    public Optional<Float> toNumberFloat(Optional<String> value) {
        return toNumberFloat(value.orElse(null));
    }

    @Override
    public Optional<Double> parseNumberDouble(String value) throws NumberFormatException {
        return toDouble(value);
    }

    @Override
    public Optional<Double> parseNumberDouble(Optional<String> value) {
        return parseNumberDouble(value.orElse(null));
    }

    @Override
    public Optional<String> toString(String value) {
        return Optional.ofNullable(value);
    }

    @Override
    public Optional<String> toString(Optional<String> value) {
        return toString(value.orElse(null));
    }

    @Override
    public Optional<byte[]> toStringByte(String value) throws IllegalArgumentException {
        return Optional.ofNullable(value == null ? null : Base64.getDecoder().decode(value));
    }

    @Override
    public Optional<byte[]> toStringByte(Optional<String> value) {
        return toStringByte(value.orElse(null));
    }

    @Override
    public Optional<byte[]> toStringBinary(String value) throws IllegalArgumentException {
        return Optional.ofNullable(value == null ? null : DatatypeConverter.parseHexBinary(value));
    }

    @Override
    public Optional<byte[]> toStringBinary(Optional<String> value) {
        return toStringBinary(value.orElse(null));
    }

    @Override
    public Optional<Boolean> toBoolean(String value) throws NumberFormatException {
        return Optional.ofNullable(value == null ? null : Boolean.valueOf(value));
    }

    @Override
    public Optional<Boolean> toBoolean(Optional<String> value) {
        return toBoolean(value.orElse(null));
    }

    @Override
    public Optional<Date> toStringDate(String value) {
        return parseDate(FULL_DATE_FORMAT, value);
    }

    @Override
    public Optional<Date> toStringDate(Optional<String> value) {
        return toStringDate(value.orElse(null));
    }

    @Override
    public Optional<Date> toStringDateDateTime(String value) {
         return parseDate(DATE_TIME_FORMAT, value);
    }

    @Override
    public Optional<Date> toStringDateDateTime(Optional<String> value) {
        return toStringDateDateTime(value.orElse(null));
    }

    @Override
    public Optional<char[]> toStringPassword(String value) {
        return Optional.ofNullable(value == null ? null : value.toCharArray());
    }

    @Override
    public Optional<char[]> toStringPassword(Optional<String> value) {
        return toStringPassword(value.orElse(null));
    }

    private static Optional<Date> parseDate(DateFormat dateFormat, String value) {
        try {
            return value == null ? Optional.empty() : Optional.of(dateFormat.parse(value));
        } catch (ParseException ex) {
            throw new IllegalArgumentException("Cannot convert '"
            + value + "' as date using " + dateFormat, ex);
        }
    }
}
