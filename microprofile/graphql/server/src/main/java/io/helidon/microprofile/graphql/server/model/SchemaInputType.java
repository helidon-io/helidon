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

/**
 * The representation of a GraphQL Input Type.
 */
public class SchemaInputType extends SchemaType {

    /**
     * Construct an {@link SchemaInputType}.
     *
     * @param name           name for the input type
     * @param valueClassName fully qualified value name
     */
    public SchemaInputType(String name, String valueClassName) {
        super(name, valueClassName);
    }

    @Override
    public void addFieldDefinition(SchemaFieldDefinition schemaFieldDefinition) {
        if (schemaFieldDefinition.getArguments().size() > 0) {
            throw new IllegalArgumentException("input types cannot have fields with arguments");
        }
        super.addFieldDefinition(schemaFieldDefinition);
    }

    @Override
    protected String getGraphQLName() {
        return "input";
    }

    @Override
    public String toString() {
        return "InputType" + toStringInternal();
    }
}
