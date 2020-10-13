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

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Collection;
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
import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.getAnnotationValue;
import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.getFieldAnnotations;
import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.getMethodAnnotations;
import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.getParameterAnnotations;

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
    protected static final String[] NO_FORMATTING = new String[] {null, null, null };

    /**
     * JsonbDateFormat class name.
     */
    private static final String JSONB_DATE_FORMAT = JsonbDateFormat.class.getName();

    /**
     * DateFormat class name.
     */
    private static final String DATE_FORMAT = org.eclipse.microprofile.graphql.DateFormat.class.getName();

    /**
     * JsonBNumberFormat class name.
     */
    private static final String JSONB_NUMBER_FORMAT = JsonbNumberFormat.class.getName();

    /**
     * NumberFormat class name.
     */
    private static final String NUMBER_FORMAT = org.eclipse.microprofile.graphql.NumberFormat.class.getName();

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
     * Returns a {@link NumberFormat} for the given type, locale and format.
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
                || DOUBLE_CLASS.equals(type)
                || DOUBLE_PRIMITIVE_CLASS.equals(type)
        ) {
            numberFormat = NumberFormat.getNumberInstance(actualLocale);
        } else if (INT.equals(type)
                || INTEGER_CLASS.equals(type)
                || INTEGER_PRIMITIVE_CLASS.equals(type)
                || BIG_INTEGER_CLASS.equals(type)
                || BYTE_CLASS.equals(type)
                || SHORT_CLASS.equals(type)
                || LONG_CLASS.equals(type)
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
     * Return a {@link DateTimeFormatter} for the given type, locale and format.
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
            // handle defaults of not format specified
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
     * either DATE, NUMBER or null if no formatting applied: [1][2] = if formatting applied then the format and locale.
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
     * Return the field format with the given index.
     *
     * @param field {@link Field} to introspect
     * @param index index of generic type. 0 = List/Collection, 1 = Map
     * @return the field format with the given index
     */
    protected static String[] getFieldFormat(Field field, int index) {
        Annotation[] annotations = getFieldAnnotations(field, index);
        if (annotations == null || annotations.length == 0) {
            // try to get standard parameter annotation.
            annotations = field.getAnnotatedType().getAnnotations();
        }

        return getFormatFromAnnotations(annotations);
    }

    /**
     * Return the method format with the given index.
     *
     * @param method {@link Method} to introspect
     * @param index     index of generic type. 0 = List/Collection, 1 = Map
     * @return the method format with the given index
     */
    protected static String[] getMethodFormat(Method method, int index) {
        Annotation[] annotations = getMethodAnnotations(method, index);
        if (annotations == null || annotations.length == 0) {
            // try to get standard parameter annotation.
            annotations = method.getAnnotatedReturnType().getAnnotations();
        }
        return getFormatFromAnnotations(annotations);
    }

    /**
     * Return the method parameter format with the given index.
     *
     * @param parameter {@link Parameter} to introspect
     * @param index     index of generic type. 0 = List/Collection, 1 = Map
     * @return the method parameter format with the given index
     */
    protected static String[] getMethodParameterFormat(Parameter parameter, int index) {
        Annotation[] annotations = getParameterAnnotations(parameter, index);
        if (annotations == null || annotations.length == 0) {
            // try to get standard parameter annotation.
            annotations = parameter.getAnnotatedType().getAnnotations();
        }

        return getFormatFromAnnotations(annotations);
    }

    /**
     * Return the formatting from the given annotations.
     *
     * @param annotations {@link Annotation}s to retrieve formatting from
     * @return the formatting from the given annotations
     */
    protected static String[] getFormatFromAnnotations(Annotation[] annotations) {
        if (annotations != null && annotations.length > 0) {
            JsonbDateFormat dateFormat1 = (JsonbDateFormat) getAnnotationValue(annotations, JsonbDateFormat.class);
            DateFormat dateFormat2 = (DateFormat) getAnnotationValue(annotations, DateFormat.class);
            JsonbNumberFormat numberFormat1 = (JsonbNumberFormat) getAnnotationValue(annotations, JsonbNumberFormat.class);
            org.eclipse.microprofile.graphql.NumberFormat numberFormat2 =
                    (org.eclipse.microprofile.graphql.NumberFormat)
                            getAnnotationValue(annotations, org.eclipse.microprofile.graphql.NumberFormat.class);

            // ensure that both date and number formatting are not present
            if ((dateFormat1 != null || dateFormat2 != null)
                    && (numberFormat1 != null || numberFormat2 != null)) {
                ensureRuntimeException(LOGGER, "Cannot have date and number formatting on the same method");
            }
            String format = null;
            String locale = null;
            if (dateFormat1 != null) {
                format = dateFormat1.value();
                locale = dateFormat1.locale();
            } else if (dateFormat2 != null) {
                format = dateFormat2.value();
                locale = dateFormat2.locale();
            } else if (numberFormat1 != null) {
                format = numberFormat1.value();
                locale = numberFormat1.locale();
            } else if (numberFormat2 != null) {
                format = numberFormat2.value();
                locale = numberFormat2.locale();
            }

            if (format == null) {
                return NO_FORMATTING;
            }

            String type = dateFormat1 != null || dateFormat2 != null ? DATE
                    : NUMBER;

            return new String[] {type, format, locale.equals("") ? DEFAULT_LOCALE : locale };

        }
        return NO_FORMATTING;
    }

    /**
     * Indicates if either {@link JsonbNumberFormat} or {@link JsonbDateFormat} are present.
     *
     * @param annotatedElement the {@link AnnotatedElement} to check
     * @return true if either {@link JsonbNumberFormat} or {@link JsonbDateFormat} are present
     */
    protected static boolean isJsonbAnnotationPresent(AnnotatedElement annotatedElement) {
        return annotatedElement.getAnnotation(JsonbDateFormat.class) != null
                || annotatedElement.getAnnotation(JsonbNumberFormat.class) != null;
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

    /**
     * Format a date value given a {@link DateTimeFormatter}.
     *
     * @param originalResult    original result
     * @param dateTimeFormatter {@link DateTimeFormatter} to format with
     * @return formatted value
     */
    @SuppressWarnings({"unchecked", "rawtypes" })
    public static Object formatDate(Object originalResult, DateTimeFormatter dateTimeFormatter) {
        if (originalResult == null) {
            return null;
        }
        if (originalResult instanceof Collection) {
            Collection formattedResult = new ArrayList();
            Collection originalCollection = (Collection) originalResult;
            originalCollection.forEach(e -> formattedResult.add(e instanceof TemporalAccessor ? dateTimeFormatter
                    .format((TemporalAccessor) e) : e)
            );
            return formattedResult;
        } else {
            return originalResult instanceof TemporalAccessor
                    ? dateTimeFormatter.format((TemporalAccessor) originalResult) : originalResult;
        }
    }

    /**
     * Format a number value with a a given {@link NumberFormat}.
     *
     * @param originalResult original result
     * @param isScalar       indicates if it is a scalar value
     * @param numberFormat   {@link NumberFormat} to format with
     * @return formatted value
     */
    @SuppressWarnings({"unchecked", "rawtypes" })
    public static Object formatNumber(Object originalResult, boolean isScalar, NumberFormat numberFormat) {
        if (originalResult == null) {
            return null;
        }
        if (originalResult instanceof Collection) {
            Collection formattedResult = new ArrayList();
            Collection originalCollection = (Collection) originalResult;
            originalCollection.forEach(e -> formattedResult.add(numberFormat.format(e)));
            LOGGER.info("Formatted result = " + formattedResult);
            return formattedResult;
        }
        return numberFormat.format(originalResult);
    }

}
