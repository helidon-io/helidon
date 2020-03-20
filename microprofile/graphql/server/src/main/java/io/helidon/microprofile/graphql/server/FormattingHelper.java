/*
 * Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Locale;

import javax.json.bind.annotation.JsonbNumberFormat;

/**
 * Helper class for number formatting.
 */
public class FormattingHelper {
    /**
     * Returna {@link NumberFormat} for the given type, locale and format.
     *
     * @param type   the GraphQL type or scalar
     * @param locale the locale, either "" or the correct locale
     * @param format the format to use, may be null
     * @return The correct {@link NumberFormat} for the given type and locale
     */
    protected static NumberFormat getCorrectFormat(String type, String locale, String format) {
        Locale actualLocale = SchemaGeneratorHelper.DEFAULT_LOCALE.equals(locale)
                ? Locale.getDefault()
                : Locale.forLanguageTag(locale);
        NumberFormat numberFormat;
        if (SchemaGeneratorHelper.FLOAT.equals(type) || SchemaGeneratorHelper.BIG_DECIMAL.equals(type)) {
            numberFormat = NumberFormat.getNumberInstance(actualLocale);
        } else if (SchemaGeneratorHelper.INT.equals(type) || SchemaGeneratorHelper.BIG_INTEGER.equals(type)) {
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
     * Returna {@link NumberFormat} for the given type and locale.
     *
     * @param type   the GraphQL type or scalar
     * @param locale the locale, either "" or the correct locale
     * @return The correct {@link NumberFormat} for the given type and locale
     */
    protected static NumberFormat getCorrectFormat(String type, String locale) {
        return getCorrectFormat(type, locale, null);
    }

    /**
     * Return the format and locale for a field if they exist in a {@link String} array.
     *
     * @param field the {@link Field} to check
     * @return the format ([0]) and locale ([1])  for a field in a {@link String} array or an empty array if not
     */
    protected static String[] getFormatAnnotation(Field field) {
        return getFormatAnnotationInternal(field.getAnnotation(JsonbNumberFormat.class), field
                .getAnnotation(org.eclipse.microprofile.graphql.NumberFormat.class));
    }

    /**
     * Return the format and locale for a parameter if they exist in a {@link String} array.
     *
     * @param parameter the {@link Field} to check
     * @return the format ([0]) and locale ([1]) for a parameter in a {@link String} array or an empty array if not
     */
    protected static String[] getFormatAnnotation(Parameter parameter) {
        return getFormatAnnotationInternal(parameter.getAnnotation(JsonbNumberFormat.class), parameter
                .getAnnotation(org.eclipse.microprofile.graphql.NumberFormat.class));
    }

    /**
     * Return the format and locale for a method if they exist in a {@link String} array.
     *
     * @param method the {@link Method} to check
     * @return the format ([0]) and locale ([1]) for a method in a {@link String} array or an empty array if not
     */
    protected static String[] getFormatAnnotation(Method method) {
        return getFormatAnnotationInternal(method.getAnnotation(JsonbNumberFormat.class),
                                           method.getAnnotation(org.eclipse.microprofile.graphql.NumberFormat.class));
    }

    /**
     * Return the format and locate for the given annotations.
     *
     * @param jsonbNumberFormat {@link JsonbNumberFormat} annotation, may be null
     * @param numberFormat      {@Link org.eclipse.microprofile.graphql.NumberFormat} annotation, may be none
     * @return the format ([0]) and locale ([1]) for a method in a {@link String} array or an empty array if not
     */
    private static String[] getFormatAnnotationInternal(JsonbNumberFormat jsonbNumberFormat,
                                                        org.eclipse.microprofile.graphql.NumberFormat numberFormat) {
        // check @NumberFormat first as this takes precedence
        if (numberFormat != null) {
            return new String[] { numberFormat.value(), numberFormat.locale() };
        }
        if (jsonbNumberFormat != null) {
            return new String[] { jsonbNumberFormat.value(), jsonbNumberFormat.locale() };
        }
        return new String[0];
    }
}
