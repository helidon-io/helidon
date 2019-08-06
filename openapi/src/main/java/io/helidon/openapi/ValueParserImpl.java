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

import javax.xml.bind.DatatypeConverter;

/**
 * Parses strings into OpenAPI datatypes.
 */
public class ValueParserImpl implements ValueParser {

    private static final String FULL_DATE_FORMAT_STRING = "yyyy-MM-dd";

    private static final SimpleDateFormat DATE_TIME_FORMAT =
            new SimpleDateFormat(FULL_DATE_FORMAT_STRING + "'T'HH:mm:ss.SSSX");

    private static final SimpleDateFormat FULL_DATE_FORMAT =
            new SimpleDateFormat(FULL_DATE_FORMAT_STRING);

    @Override
    public String converterMethod(String datatype) {
        switch (datatype) {
            case "integer":
            case "number":
            case "string":
                return datatype;
            case "boolean":
                return "bool";
            default:
                return null;
        }
    }

    @Override
    public String converterMethod(String datatype, String format) {
        switch (datatype) {
            case "integer":
                switch (format) {
                    case "int32":
                        return "integerInt32";
                    case "int64":
                        return "integerInt64";
                    default:
                        return null;
                }
            case "number":
                switch (format) {
                    case "float":
                        return "numberFloat";
                    case "double":
                        return "numberDouble";
                    default:
                        return null;
                }
            case "string":
                switch (format) {
                    case "byte":
                        return "stringByte";
                    case "binary":
                        return "stringBinary";
                    case "date":
                        return "stringDate";
                    case "date-time":
                        return "stringDateTime";
                    case "password":
                        return "stringPassword";
                    default:
                        return null;
                }
            default:
                return null;
        }
    }

    @Override
    public int integer(String value) throws NumberFormatException {
        return integerInt32(value);
    }

    @Override
    public int integerInt32(String value) throws NumberFormatException {
        return Integer.parseInt(value);
    }

    @Override
    public long integerInt64(String value) throws NumberFormatException {
        return Long.parseLong(value);
    }

    @Override
    public double number(String value) throws NumberFormatException {
        return Double.parseDouble(value);
    }

    @Override
    public float numberFloat(String value) throws NumberFormatException {
        return Float.parseFloat(value);
    }

    @Override
    public double numberDouble(String value) throws NumberFormatException {
        return Double.parseDouble(value);
    }

    @Override
    public String string(String value) {
        return value;
    }

    @Override
    public byte[] stringByte(String value) throws IllegalArgumentException {
        return Base64.getDecoder().decode(value);
    }

    @Override
    public byte[] stringBinary(String value) throws IllegalArgumentException {
        return DatatypeConverter.parseHexBinary(value);
    }

    @Override
    public boolean bool(String value) throws NumberFormatException {
        return Boolean.parseBoolean(value);
    }

    @Override
    public Date date(String value) {
        return parseDate(FULL_DATE_FORMAT, value);
    }

    @Override
    public Date dateDateTime(String value) {
         return parseDate(DATE_TIME_FORMAT, value);
    }

    @Override
    public char[] stringPassword(String value) {
        return value.toCharArray();
    }

    private static Date parseDate(DateFormat dateFormat, String value) {
        try {
            return dateFormat.parse(value);
        } catch (ParseException ex) {
            throw new IllegalArgumentException("Cannot convert '"
            + value + "' as date using " + dateFormat, ex);
        }
    }
}
