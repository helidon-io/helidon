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
import java.util.Collections;
import java.util.List;

/**
 * Utility methods for dealing with parameters passed to endpoints.
 */
public class ParameterUtil {

    /**
     * Ways to separate multiple values in OpenAPI endpoint parameters.
     *
     * Each value has a regex expression to use in separating multiple values
     * using that technique as well as the lower-case name of the format as
     * used in OpenAPI parameter definitions.
     *
     */
    private enum Separator {
        CSV(",", "csv"), // comma-separated values
        SSV(" ", "ssv"), // space-separated values
        TSV("\t", "tsv"), // tab-separated values
        PIPES("\\|", "pipes"), // the escape is needed because we use this in a regex
        MULTI(",", "multi"); // used only for query params

        private final String separatorString;
        private final String formatName;

        Separator(String separatorString, String formatName) {
            this.separatorString = separatorString;
            this.formatName = formatName;
        }


        /**
         * Returns the {@code Separator} value that matches the specified lower-
         * case name.
         *
         * @param formatName lower-case name for the separation style
         * @return {@code Separator} value matching the lower-case name
         * @throws IllegalArgumentException if no {@code Separator} matches the name
         */
        private static Separator match(String formatName) {
            for (Separator sep : Separator.values()) {
                if (sep.formatName.equals(formatName)) {
                    return sep;
                }
            }
            throw new IllegalArgumentException("No Separator matches name '" + formatName + "'");
        }
    }

    /**
     * Parses a parameter value according to the specified format.
     *
     * @param value String containing the multiple values
     * @param formatName which format the values are expressed in (csv, ssv, tsv, pipes, multi)
     * @return List of Strings corresponding to the individual values
     * @throws IllegalArgumentException if the format is not recognized
     */
    public static List<String> parser(String value, String formatName) {
        final Separator sep = Separator.match(formatName);
        final List<String> result = new ArrayList<>();
        Collections.addAll(result, value.split(sep.separatorString));
        return result;
    }
}
