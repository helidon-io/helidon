/*
 * Copyright (c) 2023, 2025 Oracle and/or its affiliates.
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

package io.helidon.codegen.classmodel;

import java.util.ArrayList;
import java.util.List;

import io.helidon.common.types.EnumValue;
import io.helidon.common.types.TypeName;

final class AnnotationParser {
    private static final System.Logger LOGGER = System.getLogger(AnnotationParser.class.getName());
    private static final String CLASS_PREFIX = "class::";
    private static final String ENUM_PREFIX = "enum::";

    private AnnotationParser() {
    }

    static Annotation parse(String annotationDefinition) {
        String annotationString = annotationDefinition.replaceAll("\n", "");
        int annotationBodyStart = annotationString.indexOf("(");
        int annotationBodyEnd = annotationString.lastIndexOf(")");
        if (annotationBodyStart > 0 || annotationBodyEnd > 0) {
            if (annotationBodyStart < 0 || annotationBodyEnd < 0) {
                throw new IllegalArgumentException("Invalid annotation definition, inconsistent braces: " + annotationDefinition);
            }
            return parseWithValues(annotationString, annotationBodyStart, annotationBodyEnd);
        }
        // this is just an annotation type
        return Annotation.builder()
                .type(annotationString)
                .build();
    }

    private static Annotation parseWithValues(String annotationDefinition, int annotationBodyStart, int annotationBodyEnd) {
        String annotationName = annotationDefinition.substring(0, annotationBodyStart).trim();
        String annotationBody = annotationDefinition.substring(annotationBodyStart + 1, annotationBodyEnd).trim();

        int equals = annotationDefinition.indexOf('=', annotationBodyStart);
        if (annotationBody.startsWith("\"")
                || annotationBody.startsWith("@")
                || equals == -1) {
            // single value
            return Annotation.builder()
                    .type(annotationName)
                    .addParameter("value", toValue(annotationDefinition.substring(annotationBodyStart + 1,
                                                                                  annotationBodyEnd)))
                    .build();
        }

        // we can have @Something as a value (it may contain nested =)
        // we may have "something = " as a value (it may contain nested =)
        return parseWithNamedValues(annotationName, annotationBody);
    }

    private static Object toValue(String value) {
        if (value.isBlank()) {
            throw new IllegalArgumentException("Invalid custom annotation value specified (blank)");
        }

        if (value.indexOf('\"') == 0) {
            // java.lang.String - expecting a quoted value, escapes of inner quotes is possible
            if (value.lastIndexOf('\"') == value.length() - 1) {
                return value.substring(1, value.length() - 1).replaceAll("\\\\", "");
            }
            throw new IllegalArgumentException("Invalid annotation value specified, inconsistent quotes: " + value);
        }
        if (value.indexOf('\'') == 0) {
            // char - if the value is in single quotes {@code '}</li>
            if (value.lastIndexOf('\'') == value.length() - 1) {
                if (value.length() == 3) {
                    // 'x'
                    // a simple character definition
                    return value.charAt(1);
                }
                if (value.length() == 4) {
                    // must be an escaped char or an error
                    // '\''

                    char backslash = value.charAt(1);
                    if (backslash != '\\') {
                        throw new IllegalArgumentException("Invalid annotation value specified, expected escaped value: "
                                                                   + value);
                    }
                    char next = value.charAt(2);
                    return switch (next) {
                        case 't' -> '\t';
                        case 'n' -> '\n';
                        case 'r' -> '\r';
                        case 'f' -> '\f';
                        case '\'' -> '\'';
                        default -> throw new IllegalArgumentException("Invalid annotation value specified, invalid escaped "
                                                                              + "value (expected one of t, n, r, f, '): "
                                                                              + value);
                    };
                } else {
                    throw new IllegalArgumentException("Invalid annotation value specified, char must be a single character,"
                                                               + "or an escaped character : " + value);
                }
            }
            throw new IllegalArgumentException("Invalid annotation value specified, inconsistent single quotes: " + value);
        }

        if (value.startsWith(CLASS_PREFIX)) {
            // class - unquoted value, in format {@code class::fq-name}, such as {@code class::java.lang.String}
            return TypeName.create(value.substring(CLASS_PREFIX.length()));
        }

        if (value.startsWith(ENUM_PREFIX)) {
            // enum - unquoted value, in format {@code enum::fq-name.NAME}, such as
            //         {@code enum::java.lang.annotation.ElementType.CONSTRUCTOR}
            String enumAndValue = value.substring(ENUM_PREFIX.length());
            int dot = enumAndValue.lastIndexOf('.');
            if (dot > 0) {
                return EnumValue.create(TypeName.create(enumAndValue.substring(0, dot)),
                                        enumAndValue.substring(dot + 1));
            }
            throw new IllegalArgumentException("Invalid annotation value specified, invalid enum (must be enum::class.VALUE): "
                                                       + value);
        }

        if (value.charAt(0) == '@') {
            // annotations - if the value starts with at sign ({@code @})</li>
            // nested annotations are always from common types
            return parse(value.substring(1)).toTypesAnnotation();
        }

        if (value.charAt(0) == '{') {
            // arrays - if the value is surrounded by curly braces {@code {}} it is an array
            if (value.charAt(value.length() - 1) != '}') {
                throw new IllegalArgumentException("Invalid annotation value specified, inconsistent array: " + value);
            }
            return parseArray(value.substring(1, value.length() - 1));
        }

        if (value.equals("true") || value.equals("false")) {
            // boolean - if the value is not quoted and is equal either to {code true} or {@code false}
            return Boolean.parseBoolean(value);
        }

        // one of the primitive types, last character defines which one
        char lastChar = value.charAt(value.length() - 1);

        /*
         *integer - if the value is not quoted and is a number
         *double - if the value is not quoted, and ends with {@code D} (capital letter d)
         *long - if the value is not quoted and ends with {@code L} (capital letter l)
         *float - if the value is not quoted and ends with {@code F} (capital letter f)
         *byte - unquoted value ending with {@code B}, such as {@code 49B}
         *short - unquoted value ending with {@code S}, such as {@code 49S}
         */

        try {
            return switch (lastChar) {
                case 'D', 'd' -> Double.parseDouble(value.substring(0, value.length() - 1));
                case 'L', 'l' -> Long.parseLong(value.substring(0, value.length() - 1));
                case 'F', 'f' -> Float.parseFloat(value.substring(0, value.length() - 1));
                case 'B', 'b' -> Byte.parseByte(value.substring(0, value.length() - 1));
                case 'S', 's' -> Short.parseShort(value.substring(0, value.length() - 1));
                default -> Integer.parseInt(value);
            };
        } catch (Exception e) {
            // this may be an older approach, for backward compatibility, assume enum
            int dot = value.lastIndexOf('.');
            if (dot > 0) {
                LOGGER.log(System.Logger.Level.WARNING, "Unquoted annotation value found, assuming enum."
                        + " Please prefix the value with 'enum::', this will be removed in future versions. "
                        + " Value: " + value);
                // backward compatibility, assume enum
                return EnumValue.create(TypeName.create(value.substring(0, dot)), value.substring(dot + 1));
            }
            throw new IllegalArgumentException("Invalid annotation value specified. Unquoted value must be one of: "
                                                       + "true, false, integer, or a number suffixed by D, L, F, B, or S "
                                                       + "(double, long, float, byte, short), or prefixed with class:: "
                                                       + "or enum::, but was: " + value);
        }
    }

    private static Object parseArray(String arrayString) {
        List<Object> list = new ArrayList<>();

        if (arrayString.isEmpty()) {
            return List.of();
        }

        if (arrayString.isBlank()) {
            // just {}
            return List.of();
        }

        int index = 0;
        while (true) {
            int nextValueEnd = nextValueEnd(arrayString, index);
            list.add(toValue(arrayString.substring(index, nextValueEnd)));
            index = nextValueEnd;

            if (arrayString.length() == index || arrayString.length() == index + 1) {
                return list;
            }

            // there must be a ",", or ", " before next value
            if (arrayString.charAt(index) != ',') {
                return list;
            }
            // after comma
            index++;
            while (Character.isWhitespace(arrayString.charAt(index))) {
                // after whitespace space
                index++;
            }
        }
    }

    private static int nextValueEnd(String valueString, int index) {
        // if string, then we can have an escaped character \"
        // if char, then we can have an escaped character \'
        if (valueString.charAt(index) == '\"') {
            // String
            // iterate until we reach an un-escaped double quotes
            char previousChar = ' ';
            for (int i = index + 1; i < valueString.length(); i++) {
                char current = valueString.charAt(i);
                if (previousChar == '\\') {
                    previousChar = current;
                    continue;
                }
                previousChar = current;
                if (current == '"') {
                    return i + 1;
                }
            }
            return valueString.length();
        }
        if (valueString.charAt(index) == '\'') {
            // char
            if (valueString.charAt(index + 1) == '\\') {
                return index + 4;
            }
            return index + 3;
        }
        if (valueString.charAt(index) == '{') {
            // array
            int arrayIndex = index;
            while (true) {
                int end = nextValueEnd(valueString, arrayIndex + 1);
                int nextComma = nextComma(valueString, end);
                int nextArrayEnd = nextArrayEnd(valueString, end);
                if (nextComma != -1 && nextArrayEnd != -1) {
                    if (nextComma < nextArrayEnd) {
                        arrayIndex = nextComma;
                        continue;
                    }
                    return nextArrayEnd + 1;
                }
                if (nextComma != -1) {
                    arrayIndex = nextComma;
                    continue;
                } else if (nextArrayEnd != -1) {
                    return nextArrayEnd + 1;
                }
                throw new IllegalArgumentException("Invalid annotation value specified. Inconsistent array: " + valueString);
            }
        }
        // anything else ends with: , }
        for (int i = index; i < valueString.length(); i++) {
            char current = valueString.charAt(i);
            switch (current) {
            case ',', ' ', '}', '\n', '\r', '\t':
                return i;
            default:
            }
        }
        return valueString.length();
    }

    private static int nextArrayEnd(String valueString, int index) {
        for (int i = index; i < valueString.length(); i++) {
            if (valueString.charAt(i) == '}') {
                return i;
            }
        }
        return -1;
    }

    private static int nextComma(String valueString, int index) {
        for (int i = index; i < valueString.length(); i++) {
            if (valueString.charAt(i) == ',') {
                return i;
            }
        }
        return -1;
    }

    private static Annotation parseWithNamedValues(String annotationName,
                                                   String annotationBody) {
        Annotation.Builder builder = Annotation.builder()
                .type(annotationName);

        /*
        key = "some value"
         */
        int equals = annotationBody.indexOf('=');
        String remainder = annotationBody;

        while (true) {
            String key = remainder.substring(0, equals).trim();
            remainder = remainder.substring(equals + 1).trim();
            int end = nextValueEnd(remainder, 0);
            Object value = toValue(remainder.substring(0, end));
            builder.addParameter(param -> param.name(key)
                    .value(value));

            if (remainder.length() <= end + 1) {
                return builder.build();
            }

            remainder = remainder.substring(end + 1).trim();
            equals = remainder.indexOf('=');
            if (equals == -1) {
                throw new IllegalArgumentException("Invalid annotation value specified, unexpected values structure: "
                                                           + annotationBody);
            }
        }
    }
}
