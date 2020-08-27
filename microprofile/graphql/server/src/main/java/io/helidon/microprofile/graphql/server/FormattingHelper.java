/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

package io.helidon.microprofile.graphql.server;

import java.lang.reflect.AnnotatedElement;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.logging.Logger;

import javax.json.bind.annotation.JsonbDateFormat;
import javax.json.bind.annotation.JsonbNumberFormat;

import org.eclipse.microprofile.graphql.DateFormat;

import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.BIG_DECIMAL;
import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.BIG_DECIMAL_CLASS;
import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.BIG_INTEGER;
import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.BIG_INTEGER_CLASS;
import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.BYTE_CLASS;
import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.DEFAULT_LOCALE;
import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.DOUBLE_CLASS;
import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.DOUBLE_PRIMITIVE_CLASS;
import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.FLOAT;
import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.FLOAT_CLASS;
import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.FLOAT_PRIMITIVE_CLASS;
import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.INT;
import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.INTEGER_CLASS;
import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.INTEGER_PRIMITIVE_CLASS;
import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.LOCAL_DATE_CLASS;
import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.LOCAL_DATE_TIME_CLASS;
import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.LOCAL_TIME_CLASS;
import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.LONG_CLASS;
import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.LONG_PRIMITIVE_CLASS;
import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.OFFSET_DATE_TIME_CLASS;
import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.OFFSET_TIME_CLASS;
import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.SHORT_CLASS;
import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.SUPPORTED_SCALARS;
import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.ZONED_DATE_TIME_CLASS;
import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.ensureRuntimeException;

/**
 * Helper class for number formatting.
 */
public class FormattingHelper {

    /**
     * Logger.
     */
    private static final Logger LOGGER = Logger.getLogger(FormattingHelper.class.getName());

    /**
     * Defines no default format.
     */
    private static final String[] NO_DEFAULT_FORMAT = new String[0];

    /**
     * Indicates date formatting applied.
     */
    protected static final String DATE = "Date";

    /**
     * Indicates number formatting applied.
     */
    protected static final String NUMBER = "Number";

    /**
     * Indicates no formatting applied.
     */
    private static final String[] NO_FORMATTING = new String[] {null, null, null };

    /**
     * No-args constructor.
     */
    private FormattingHelper() {
    }

    /**
     * Return the default date/time format for the given class.
     *
     * @param scalarName scalar to check
     * @param clazzName  class name to validate against
     * @return he default date/time format for the given class
     */
    protected static String[] getDefaultDateTimeFormat(String scalarName, String clazzName) {
        for (SchemaScalar scalar : SUPPORTED_SCALARS.values()) {
            if (scalarName.equals(scalar.getName()) && scalar.getActualClass().equals(clazzName)) {
                return new String[] {scalar.getDefaultFormat(), DEFAULT_LOCALE };
            }
        }
        return NO_DEFAULT_FORMAT;
    }

    /**
     * Returna {@link NumberFormat} for the given type, locale and format.
     *
     * @param type   the GraphQL type or scalar
     * @param locale the locale, either "" or the correct locale
     * @param format the format to use, may be null
     * @return The correct {@link NumberFormat} for the given type and locale
     */
    protected static NumberFormat getCorrectNumberFormat(String type, String locale, String format) {
        Locale actualLocale = DEFAULT_LOCALE.equals(locale)
                ? Locale.getDefault()
                : Locale.forLanguageTag(locale);
        NumberFormat numberFormat;
        if (FLOAT.equals(type)
                || BIG_DECIMAL.equals(type)
                || BIG_DECIMAL_CLASS.equals(type)
                || FLOAT_CLASS.equals(type)
                || FLOAT_PRIMITIVE_CLASS.equals(type)
        ) {
            numberFormat = NumberFormat.getNumberInstance(actualLocale);
        } else if (INT.equals(type)
                || INTEGER_CLASS.equals(type)
                || INTEGER_PRIMITIVE_CLASS.equals(type)
                || BIG_INTEGER_CLASS.equals(type)
                || BYTE_CLASS.equals(type)
                || SHORT_CLASS.equals(type)
                || LONG_CLASS.equals(type)
                || DOUBLE_CLASS.equals(type)
                || DOUBLE_PRIMITIVE_CLASS.equals(type)
                || LONG_PRIMITIVE_CLASS.equals(type)
                || BIG_INTEGER.equals(type)) {
            numberFormat = NumberFormat.getIntegerInstance(actualLocale);
        } else {
            return null;
        }
        if (format != null && !format.trim().equals("")) {
            ((DecimalFormat) numberFormat).applyPattern(format);
        }
        return numberFormat;
    }

    /**
     * Returna {@link DateTimeFormatter} for the given type, locale and format.
     *
     * @param type   the GraphQL type or scalar
     * @param locale the locale, either "" or the correct locale
     * @param format the format to use, may be null
     * @return The correct {@link java.text.DateFormat } for the given type and locale
     */
    protected static DateTimeFormatter getCorrectDateFormatter(String type, String locale, String format) {
        Locale actualLocale = DEFAULT_LOCALE.equals(locale)
                ? Locale.getDefault()
                : Locale.forLanguageTag(locale);

        DateTimeFormatter formatter;

        if (format != null) {
            formatter = DateTimeFormatter.ofPattern(format, actualLocale);
        } else {
            // handle defaults if not format specified
            if (OFFSET_TIME_CLASS.equals(type)) {
                formatter = DateTimeFormatter.ISO_OFFSET_TIME.withLocale(actualLocale);
            } else if (LOCAL_TIME_CLASS.equals(type)) {
                formatter = DateTimeFormatter.ISO_LOCAL_TIME.withLocale(actualLocale);
            } else if (OFFSET_DATE_TIME_CLASS.equals(type)) {
                formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME.withLocale(actualLocale);
            } else if (ZONED_DATE_TIME_CLASS.equals(type)) {
                formatter = DateTimeFormatter.ISO_ZONED_DATE_TIME.withLocale(actualLocale);
            } else if (LOCAL_DATE_TIME_CLASS.equals(type)) {
                formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME.withLocale(actualLocale);
            } else if (LOCAL_DATE_CLASS.equals(type)) {
                formatter = DateTimeFormatter.ISO_LOCAL_DATE.withLocale(actualLocale);
            } else {
                return null;
            }
        }
        return formatter;
    }

    /**
     * Return a {@link NumberFormat} for the given type and locale.
     *
     * @param type   the GraphQL type or scalar
     * @param locale the locale, either "" or the correct locale
     * @return The correct {@link NumberFormat} for the given type and locale
     */
    protected static NumberFormat getCorrectNumberFormat(String type, String locale) {
        return getCorrectNumberFormat(type, locale, null);
    }

    /**
     * Return formatting annotation details for the {@link AnnotatedElement}. The array returned contains three elements: [0] =
     * either DATE,NUMBER or null if no formatting applied: [1][2] = if formatting applied then the format and locale.
     *
     * @param annotatedElement the {@link AnnotatedElement} to check
     * @return formatting annotation details or array with three nulls if no formatting
     */
    protected static String[] getFormattingAnnotation(AnnotatedElement annotatedElement) {
        String[] dateFormat = getDateFormatAnnotation(annotatedElement);
        String[] numberFormat = getNumberFormatAnnotation(annotatedElement);

        if (dateFormat.length == 0 && numberFormat.length == 0) {
            return NO_FORMATTING;
        }
        if (dateFormat.length == 2 && numberFormat.length == 2) {
            ensureRuntimeException(LOGGER, "A date format and number format cannot be applied to the same element: "
                    + annotatedElement);
        }

        return dateFormat.length == 2
                ? new String[] {DATE, dateFormat[0], dateFormat[1] }
                : new String[] {NUMBER, numberFormat[0], numberFormat[1] };
    }

    /**
     * Return the number format and locale for an {@link AnnotatedElement} if they exist in a {@link String} array.
     *
     * @param annotatedElement the {@link AnnotatedElement} to check
     * @return the format ([0]) and locale ([1]) for a parameter in a {@link String} array or an empty array if not
     */
    protected static String[] getNumberFormatAnnotation(AnnotatedElement annotatedElement) {
        return getNumberFormatAnnotationInternal(
                annotatedElement.getAnnotation(JsonbNumberFormat.class),
                annotatedElement.getAnnotation(org.eclipse.microprofile.graphql.NumberFormat.class));
    }

    /**
     * Return the number format and locale for the given annotations.
     *
     * @param jsonbNumberFormat {@link JsonbNumberFormat} annotation, may be null
     * @param numberFormat      {@link NumberFormat} annotation, may be none
     * @return the format ([0]) and locale ([1]) for a method in a {@link String} array or an empty array if not
     */
    private static String[] getNumberFormatAnnotationInternal(JsonbNumberFormat jsonbNumberFormat,
                                                              org.eclipse.microprofile.graphql.NumberFormat numberFormat) {
        // check @NumberFormat first as this takes precedence
        if (numberFormat != null) {
            return new String[] {numberFormat.value(), numberFormat.locale() };
        }
        if (jsonbNumberFormat != null) {
            return new String[] {jsonbNumberFormat.value(), jsonbNumberFormat.locale() };
        }
        return new String[0];
    }

    /**
     * Return the date format and locale for a field if they exist in a {@link String} array.
     *
     * @param annotatedElement the {@link AnnotatedElement} to check
     * @return the format ([0]) and locale ([1])  for a field in a {@link String} array or an empty array if not
     */
    protected static String[] getDateFormatAnnotation(AnnotatedElement annotatedElement) {
        return getDateFormatAnnotationInternal(
                annotatedElement.getAnnotation(JsonbDateFormat.class),
                annotatedElement.getAnnotation(org.eclipse.microprofile.graphql.DateFormat.class));
    }

    /**
     * Return the date format and locale for the given annotations.
     *
     * @param jsonbDateFormat {@link JsonbDateFormat} annotation, may be null
     * @param dateFormat      {@link DateFormat} annotation, may be none
     * @return the format ([0]) and locale ([1]) for a method in a {@link String} array or an empty array if not
     */
    private static String[] getDateFormatAnnotationInternal(JsonbDateFormat jsonbDateFormat,
                                                            DateFormat dateFormat) {
        // check @DateFormat first as this takes precedence
        if (dateFormat != null) {
            return new String[] {dateFormat.value(), dateFormat.locale() };
        }
        if (jsonbDateFormat != null) {
            return new String[] {jsonbDateFormat.value(), jsonbDateFormat.locale() };
        }
        return new String[0];
    }

}
