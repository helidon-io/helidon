/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates.
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
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import graphql.schema.DataFetcher;

/**
 * The representation of a GraphQL Field Definition.
 */
public class SchemaFieldDefinition extends AbstractDescriptiveElement implements ElementGenerator {
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
     * If the return type is an array then indicates if the value in the
     * array is mandatory.
     */
    private boolean isArrayReturnTypeMandatory;

    /**
     * {@link DataFetcher} to override default behaviour of field.
     */
    private DataFetcher dataFetcher;

    /**
     * Original type before it was converted to a GraphQL representation.
     */
    private Class<?> originalType;

    /**
     * Original array inner type if it is array type.
     */
    private Class<?> originalArrayType;

    /**
     * Defines the format for a number or date.
     */
    private String[] format;

    /**
     * The default value for this field definition. Only valid for field definitions of an input type.
     */
    private Object defaultValue;

    /**
     * Indicates if the field has a default format applied (such as default date format) rather than a specific format supplied.
     */
    private boolean defaultFormatApplied;

    /**
     * Indicates if the format is of type Jsonb.
     */
    private boolean isJsonbFormat;

    /**
     * Indicates if the property name is of type Jsonb.
     */
    private boolean isJsonbProperty;


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
        StringBuilder sb = new StringBuilder(getSchemaElementDescription(getFormat()))
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
                    .append(isArrayReturnTypeMandatory() ? MANDATORY : NOTHING)
                    .append(repeat(count, CLOSE_SQUARE));
        } else {
            sb.append(SPACER).append(getReturnType());
        }

        if (isReturnTypeMandatory()) {
            sb.append(MANDATORY);
        }

        if (defaultValue != null) {
            sb.append(generateDefaultValue(defaultValue, getReturnType()));
        }

        return sb.toString();
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

    /**
     * Return the format for a number or date.
     *
     * @return the format for a number or date
     */
    public String[] getFormat() {
        return format;
    }

    /**
     * Set the format for a number or date.
     *
     * @param format the format for a number or date
     */
    public void setFormat(String[] format) {
        this.format = format;
    }

    /**
     * Return the default value for this field definition.
     *
     * @return the default value for this field definition
     */
    public Object getDefaultValue() {
        return defaultValue;
    }

    /**
     * Set the default value for this field definition.
     *
     * @param defaultValue the default value for this field definition
     */
    public void setDefaultValue(Object defaultValue) {
        this.defaultValue = defaultValue;
    }

    /**
     * Sets the original return type.
     *
     * @param originalType the original return type
     */
    public void setOriginalType(Class<?> originalType) {
        this.originalType = originalType;
    }

    /**
     * Retrieve the original return type.
     *
     * @return the original return type
     */
    public Class<?> getOriginalType() {
        return originalType;
    }

    /**
     * Sets the original array type.
     *
     * @param originalArrayType the original array type
     */
    public void setOriginalArrayType(Class<?> originalArrayType) {
        this.originalArrayType = originalArrayType;
    }

    /**
     * Returns the original array type.
     *
     * @return the original array type
     */
    public Class<?> getOriginalArrayType() {
        return originalArrayType;
    }

    /**
     * Return if the array return type is mandatory.
     * @return if the array return type is mandatory
     */
    public boolean isArrayReturnTypeMandatory() {
        return isArrayReturnTypeMandatory;
    }

    /**
     * Sets if the array return type is mandatory.
     * @param arrayReturnTypeMandatory if the array return type is mandatory
     */
    public void setArrayReturnTypeMandatory(boolean arrayReturnTypeMandatory) {
        isArrayReturnTypeMandatory = arrayReturnTypeMandatory;
    }

    /**
     * Set if the field has a default format applied.
     * @param defaultFormatApplied if the field has a default format applied
     */
    public void setDefaultFormatApplied(boolean defaultFormatApplied) {
        this.defaultFormatApplied = defaultFormatApplied;
    }

    /**
     * Return if the field has a default format applied.
     * @return if the field has a default format applied
     */
    public boolean isDefaultFormatApplied() {
        return defaultFormatApplied;
    }

    /**
     * Set if the format is of type Jsonb.
     * @param isJsonbFormat if the format is of type Jsonb
     */
    public void setJsonbFormat(boolean isJsonbFormat) {
        this.isJsonbFormat = isJsonbFormat;
    }

    /**
     * Returns true if the format is of type Jsonb.
     * @return true if the format is of type Jsonb
     */
    public boolean isJsonbFormat() {
        return isJsonbFormat;
    }

    /**
     * Sets if the property has a JsonbProperty annotation.
     *
     * @param isJsonbProperty if the property has a JsonbProperty annotation
     */
    public void setJsonbProperty(boolean isJsonbProperty) {
        this.isJsonbProperty = isJsonbProperty;
    }

    /**
     * Indicates if the property has a JsonbProperty annotation.
     *
     * @return true if the property has a JsonbProperty annotation
     */
    public boolean isJsonbProperty() {
        return isJsonbProperty;
    }

    @Override
    public String toString() {
        return "FieldDefinition{"
                + "name='" + name + '\''
                + ", returnType='" + returnType + '\''
                + ", isArrayReturnType=" + isArrayReturnType
                + ", isReturnTypeMandatory=" + isReturnTypeMandatory
                + ", isArrayReturnTypeMandatory=" + isArrayReturnTypeMandatory
                + ", listArguments=" + listSchemaArguments
                + ", arrayLevels=" + arrayLevels
                + ", originalType=" + originalType
                + ", defaultFormatApplied=" + defaultFormatApplied
                + ", originalArrayType=" + originalArrayType
                + ", format=" + Arrays.toString(format)
                + ", isJsonbFormat=" + isJsonbFormat
                + ", isJsonbProperty=" + isJsonbProperty
                + ", description='" + getDescription() + '\'' + '}';
    }
}
