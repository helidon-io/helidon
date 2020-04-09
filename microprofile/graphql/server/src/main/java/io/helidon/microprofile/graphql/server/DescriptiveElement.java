/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates. All rights reserved.
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

import static io.helidon.microprofile.graphql.server.ElementGenerator.NEWLINE;
import static io.helidon.microprofile.graphql.server.ElementGenerator.NOTHING;
import static io.helidon.microprofile.graphql.server.ElementGenerator.QUOTE;
import static io.helidon.microprofile.graphql.server.ElementGenerator.TRIPLE_QUOTE;
import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.getDefaultDescription;

/**
 * Describes an element that has a description.
 */
public interface DescriptiveElement {

    /**
     * Set the description for this element.
     *
     * @param description the description for this element
     */
    void setDescription(String description);

    /**
     * Return the description for this element.
     *
     * @return the description for this element
     */
    String getDescription();

    /**
     * Return the description of the schema element. Only valid for Type, Field, Method, Parameter
     *
     * @param format the format for the element
     * @return the description of the schema element.
     */
    default String getSchemaElementDescription(String[] format) {
        String description = getDefaultDescription(format, getDescription());

        if (description != null) {
            // if the description contains a quote or newline then use the triple quote option
            boolean useNormalQuotes = description.indexOf('\n') == -1 && description.indexOf('"') == -1;

            return new StringBuilder()
                    .append(useNormalQuotes
                                    ? QUOTE : (TRIPLE_QUOTE + NEWLINE))
                    .append(description)
                    .append(useNormalQuotes
                                    ? QUOTE : (NEWLINE + TRIPLE_QUOTE))
                    .append(NEWLINE)
                    .toString();
        }
        return NOTHING;
    }
}
