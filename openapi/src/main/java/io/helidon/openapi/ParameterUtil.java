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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Utility methods for dealing with parameters passed to endpoints.
 */
public class ParameterUtil {

    private ParameterUtil() {}

    /**
     * Ways to separate multiple values in OpenAPI endpoint parameters.
     *
     * Each enum value has a regex expression to use in separating multiple
     * values using that technique. By convention, each value name is the
     * corresponding format as used in OpenAPI parameter definitions.
     *
     */
    private enum Separator {
        CSV(","), // comma-separated values
        SSV(" "), // space-separated values
        TSV("\t"), // tab-separated values
        PIPES("\\|"), // the escape is needed because we use this in a regex
        MULTI(","); // used only for query params

        private final String separatorString;

        Separator(String separatorString) {
            this.separatorString = separatorString;
        }

        /**
         * Returns the {@code Separator} value that matches the specified
         * format name.
         * @param formatName name of the formatting used for multiple values
         * @return Separator for the corresponding format name
         */
        private static Separator match(String formatName) {
            // Use the English locale because the format names are not localized.
            return Enum.valueOf(Separator.class, formatName.toUpperCase(Locale.ENGLISH));
        }
    }

    /**
     * Parses a parameter value according to the specified format.
     *
     * @param multiValue String containing the multiple values
     * @param formatName which format the multi-values are expressed in (csv, ssv,
     * tsv, pipes, multi)
     * @return List of Strings corresponding to the individual values
     * @throws IllegalArgumentException if the format is not recognized
     */
    public static List<String> parse(String multiValue, String formatName) {
        final Separator sep = Separator.match(formatName);
        final List<String> result = new ArrayList<>();
        Collections.addAll(result, multiValue.split(sep.separatorString));
        return result;
    }

    /**
     * Parses a collection of parameter values, possibly expressing multiple values
     * in each, according to the specified format.
     * <p>
     * This method is particularly useful, for example, in conveniently processing all
     * occurrences of a header from a request.
     *
     * @param multiValues Collection of Strings, each of which might contain multiple value settings
     * @param formatName which format the multi-values are expressed in
     * @return List of Strings corresponding to all the individual values from all
     * the multi-values provided
     * @throws IllegalArgumentException if the format is not recognized
     */
    public static List<String> parse(Collection<String> multiValues, String formatName) {
        final List<String> result = new ArrayList<>();
        for (String multiValue : multiValues) {
            result.addAll(parse(multiValue, formatName));
        }
        return result;
    }
}
