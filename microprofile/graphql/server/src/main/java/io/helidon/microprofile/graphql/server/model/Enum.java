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

package io.helidon.microprofile.graphql.server.model;

import java.util.ArrayList;
import java.util.List;


/**
 * The representation of a GraphQL Enum.
 */
public class Enum extends AbstractDescriptiveElement
        implements SchemaGenerator {

    /**
     * The name of the enum.
     */
    private String name;

    /**
     * The values for the enum.
     */
    private List<String> values;

    public Enum(String name) {
        this.name   = name;
        this.values = new ArrayList<>();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<String> getValues() {
        return values;
    }

    public void addValue(String value) {
        this.values.add(value);
    }

    @Override
    public String getSchemaAsString() {
        StringBuilder sb = new StringBuilder(getSchemaElementDescription())
           .append("enum")
           .append(SPACER)
           .append(getName())
           .append(SPACER)
           .append(OPEN_CURLY)
           .append(NEWLINE);
        
        values.forEach(v -> sb.append(SPACER).append(v).append(NEWLINE));

        return sb.append(CLOSE_CURLY).append(NEWLINE).toString();
    }

    @Override
    public String toString() {
        return "Enum{" +
                "name='" + name + '\'' +
                ", values=" + values +
                ", description='" + description + '\'' +
                '}';
    }
}
