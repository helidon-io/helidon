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
import java.util.stream.Collectors;

import graphql.schema.DataFetcher;

/**
 * The representation of a GraphQL Field Definition.
 */
public class FieldDefinition extends AbstractDescriptiveElement
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
     * Indicates if the return type is mandatory.
     */
    private final boolean isReturnTypeMandatory;

    /**
     * List of arguments.
     */
    private final List<Argument> listArguments;

    /**
     * {@link DataFetcher} to override default behaviour of field.
     */
    private DataFetcher dataFetcher;

    /**
     * Construct a {@link FieldDefinition}.
     *
     * @param name                   field definition name
     * @param returnType             return type
     * @param isArrayReturnType      indicates if the return type is an array type such as a native array([]) or a List, Collection, etc
     * @param isReturnTypeMandatory  indicates if the return type is mandatory.
     */
    public FieldDefinition(String name, String returnType, boolean isArrayReturnType, boolean isReturnTypeMandatory) {
        this.name                  = name;
        this.returnType            = returnType;
        this.listArguments         = new ArrayList<>();
        this.isArrayReturnType     = isArrayReturnType;
        this.isReturnTypeMandatory = isReturnTypeMandatory;
    }

    @Override
    public String getSchemaAsString() {
        StringBuilder sb = new StringBuilder(getSchemaElementDescription())
                .append(getName());

        // set compact mode if no argument descriptions exist
        boolean isCompact = listArguments.stream().filter(a -> a.getDescription() != null).count() == 0;

        if (listArguments.size() > 0) {
            sb.append(OPEN_PARENTHESES).append(isCompact ? NOTHING : NEWLINE);
            sb.append(listArguments.stream()
                              .map(Argument::getSchemaAsString)
                              .collect(Collectors.joining(isCompact ? COMMA_SPACE : COMMA_NEWLINE)));
            sb.append(isCompact ? NOTHING : NEWLINE).append(CLOSE_PARENTHESES);
        }

        sb.append(COLON);

        if (isArrayReturnType()) {
            sb.append(SPACER).append(OPEN_SQUARE).append(getReturnType()).append(CLOSE_SQUARE);
        } else {
            sb.append(SPACER).append(getReturnType());
        }

        if (isReturnTypeMandatory()) {
            sb.append(MANDATORY);
        }

        return sb.toString();
    }

    /**
     * Return the name for the field definition.
     *
     * @return  the name for the field definition
     */
    public String getName() {
        return name;
    }

    /**
     * Return the {@link List} of {@link Argument}s.
     * @return the {@link List} of {@link Argument}s
     */
    public List<Argument> getArguments() {
        return listArguments;
    }

    /**
     * Return the return type.
     * @return the return type
     */
    public String getReturnType() {
        return returnType;
    }

    /**
     * Indicates if the return type is an array type.
     * @return  if the return type is an array type
     */
    public boolean isArrayReturnType() {
        return isArrayReturnType;
    }

    /**
     * Indicates if the return type is mandatory.
     * @return  if the return type is mandatory
     */
    public boolean isReturnTypeMandatory() {
        return isReturnTypeMandatory;
    }

    /**
     * Return the {@link DataFetcher} for this {@link FieldDefinition}.
     * @return he {@link DataFetcher} for this {@link FieldDefinition}
     */
    public DataFetcher getDataFetcher() {
        return dataFetcher;
    }

    /**
     * Set the return type.
     * @param sReturnType the return type
     */
    public void setReturnType(String sReturnType) {
        returnType = sReturnType;
    }

    /**
     * Set the {@link DataFetcher} which will override the default {@link DataFetcher} for the field.
     * @param dataFetcher  the {@link DataFetcher}
     */
    public void setDataFetcher(DataFetcher dataFetcher) {
        this.dataFetcher = dataFetcher;
    }

    /**
     * Add a {@link Argument} to this {@link FieldDefinition}.
     *
     * @param argument {@link Argument} to add
     */
    public void addArgument(Argument argument) {
        listArguments.add(argument);
    }

    @Override
    public String toString() {
        return "FieldDefinition{"
                + "name='" + name + '\''
                + ", returnType='" + returnType + '\''
                + ", isArrayReturnType=" + isArrayReturnType
                + ", isReturnTypeMandatory=" + isReturnTypeMandatory
                + ", listArguments=" + listArguments
                + ", description='" + getDescription() + '\'' + '}';
    }
}
