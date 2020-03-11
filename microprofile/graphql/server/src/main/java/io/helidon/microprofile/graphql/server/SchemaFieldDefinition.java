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

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import graphql.schema.DataFetcher;

/**
 * The representation of a GraphQL Field Definition.
 */
public class SchemaFieldDefinition extends AbstractDescriptiveElement
        implements SchemaGenerator {
    /**
     * Name of the field definition.
     */
    private final String name;

    /**
     * Return type.
     */
    private String returnType;

    /**
     * Indicates if the return type is an array type such as a native array([]) or a List, Collection, etc.
     */
    private final boolean isArrayReturnType;

    /**
     * The number of array levels if return type is an array.
     */
    private final int arrayLevels;

    /**
     * Indicates if the return type is mandatory.
     */
    private final boolean isReturnTypeMandatory;

    /**
     * List of arguments.
     */
    private final List<SchemaArgument> listSchemaArguments;

    /**
     * {@link DataFetcher} to override default behaviour of field.
     */
    private DataFetcher dataFetcher;

    /**
     * Construct a {@link SchemaFieldDefinition}.
     *
     * @param name                  field definition name
     * @param returnType            return type
     * @param isArrayReturnType     indicates if the return type is an array type such as a native array([]) or a List,
     *                              Collection, etc
     * @param isReturnTypeMandatory indicates if the return type is mandatory.
     * @param arrayLevels           the number of array levels if return type is an array.
     */
    public SchemaFieldDefinition(String name,
                                 String returnType,
                                 boolean isArrayReturnType,
                                 boolean isReturnTypeMandatory,
                                 int arrayLevels) {
        this.name = name;
        this.returnType = returnType;
        this.listSchemaArguments = new ArrayList<>();
        this.isArrayReturnType = isArrayReturnType;
        this.isReturnTypeMandatory = isReturnTypeMandatory;
        this.arrayLevels = arrayLevels;
    }

    @Override
    public String getSchemaAsString() {
        StringBuilder sb = new StringBuilder(getSchemaElementDescription())
                .append(getName());

        if (listSchemaArguments.size() > 0) {
            sb.append(OPEN_PARENTHESES)
                    .append(NEWLINE)
                    .append(listSchemaArguments.stream()
                                    .map(SchemaArgument::getSchemaAsString)
                                    .collect(Collectors.joining(COMMA_SPACE + NEWLINE)));
            sb.append(NEWLINE).append(CLOSE_PARENTHESES);
        }

        sb.append(COLON);

        if (isArrayReturnType()) {
            int count = getArrayLevels();
            sb.append(SPACER).append(repeat(count, OPEN_SQUARE))
                    .append(getReturnType())
                    .append(repeat(count, CLOSE_SQUARE));
        } else {
            sb.append(SPACER).append(getReturnType());
        }

        if (isReturnTypeMandatory()) {
            sb.append(MANDATORY);
        }

        return sb.toString();
    }

    /**
     * Repeate create a {@link String} with the value repeated the requested number of times.
     *
     * @param count  number of times to repeat
     * @param string {@link String} to repeat
     * @return a new {@link String}
     */
    private String repeat(int count, String string) {
        return new String(new char[count]).replace("\0", string);
    }

    /**
     * Return the name for the field definition.
     *
     * @return the name for the field definition
     */
    public String getName() {
        return name;
    }

    /**
     * Return the {@link List} of {@link SchemaArgument}s.
     *
     * @return the {@link List} of {@link SchemaArgument}s
     */
    public List<SchemaArgument> getArguments() {
        return listSchemaArguments;
    }

    /**
     * Return the return type.
     *
     * @return the return type
     */
    public String getReturnType() {
        return returnType;
    }

    /**
     * Indicates if the return type is an array type.
     *
     * @return if the return type is an array type
     */
    public boolean isArrayReturnType() {
        return isArrayReturnType;
    }

    /**
     * Indicates if the return type is mandatory.
     *
     * @return if the return type is mandatory
     */
    public boolean isReturnTypeMandatory() {
        return isReturnTypeMandatory;
    }

    /**
     * Return the {@link DataFetcher} for this {@link SchemaFieldDefinition}.
     *
     * @return he {@link DataFetcher} for this {@link SchemaFieldDefinition}
     */
    public DataFetcher getDataFetcher() {
        return dataFetcher;
    }

    /**
     * Set the return type.
     *
     * @param sReturnType the return type
     */
    public void setReturnType(String sReturnType) {
        returnType = sReturnType;
    }

    /**
     * Set the {@link DataFetcher} which will override the default {@link DataFetcher} for the field.
     *
     * @param dataFetcher the {@link DataFetcher}
     */
    public void setDataFetcher(DataFetcher dataFetcher) {
        this.dataFetcher = dataFetcher;
    }

    /**
     * Add a {@link SchemaArgument} to this {@link SchemaFieldDefinition}.
     *
     * @param schemaArgument {@link SchemaArgument} to add
     */
    public void addArgument(SchemaArgument schemaArgument) {
        listSchemaArguments.add(schemaArgument);
    }

    /**
     * Return the number of array levels if return type is an array.
     *
     * @return the number of array levels if return type is an array
     */
    public int getArrayLevels() {
        return arrayLevels;
    }

    @Override
    public String toString() {
        return "FieldDefinition{"
                + "name='" + name + '\''
                + ", returnType='" + returnType + '\''
                + ", isArrayReturnType=" + isArrayReturnType
                + ", isReturnTypeMandatory=" + isReturnTypeMandatory
                + ", listArguments=" + listSchemaArguments
                + ", arrayLevels=" + arrayLevels
                + ", description='" + getDescription() + '\'' + '}';
    }
}
