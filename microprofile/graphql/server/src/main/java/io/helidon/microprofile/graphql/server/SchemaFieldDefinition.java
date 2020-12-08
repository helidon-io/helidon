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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import graphql.schema.DataFetcher;

/**
 * The representation of a GraphQL Field Definition.
 */
class SchemaFieldDefinition extends AbstractDescriptiveElement implements ElementGenerator {
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
     * @param builder the {@link Builder} to construct from
     */
    private SchemaFieldDefinition(Builder builder) {
        this.name = builder.name;
        this.returnType = builder.returnType;
        this.isArrayReturnType = builder.isArrayReturnType;
        this.arrayLevels = builder.arrayLevels;
        this.isReturnTypeMandatory = builder.isReturnTypeMandatory;
        this.listSchemaArguments = builder.listSchemaArguments;
        this.isArrayReturnTypeMandatory = builder.isArrayReturnTypeMandatory;
        this.dataFetcher = builder.dataFetcher;
        this.originalType = builder.originalType;
        this.originalArrayType = builder.originalArrayType;
        this.format = builder.format;
        this.defaultValue = builder.defaultValue;
        this.defaultFormatApplied = builder.defaultFormatApplied;
        this.isJsonbFormat = builder.isJsonbFormat;
        this.isJsonbProperty = builder.isJsonbProperty;
        description(builder.description);
    }

    /**
     * Fluent API builder to create {@link SchemaFieldDefinition}.
     *
     * @return new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String getSchemaAsString() {
        StringBuilder sb = new StringBuilder(getSchemaElementDescription(format()))
                .append(name());

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
            int count = arrayLevels();
            sb.append(SPACER).append(repeat(count, OPEN_SQUARE))
                    .append(returnType())
                    .append(isArrayReturnTypeMandatory() ? MANDATORY : NOTHING)
                    .append(repeat(count, CLOSE_SQUARE));
        } else {
            sb.append(SPACER).append(returnType());
        }

        if (isReturnTypeMandatory()) {
            sb.append(MANDATORY);
        }

        if (defaultValue != null) {
            sb.append(generateDefaultValue(defaultValue, returnType()));
        }

        return sb.toString();
    }

    /**
     * Return the name for the field definition.
     *
     * @return the name for the field definition
     */
    public String name() {
        return name;
    }

    /**
     * Return the {@link List} of {@link SchemaArgument}s.
     *
     * @return the {@link List} of {@link SchemaArgument}s
     */
    public List<SchemaArgument> arguments() {
        return listSchemaArguments;
    }

    /**
     * Return the return type.
     *
     * @return the return type
     */
    public String returnType() {
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
    public DataFetcher dataFetcher() {
        return dataFetcher;
    }

    /**
     * Set the return type.
     *
     * @param sReturnType the return type
     */
    public void returnType(String sReturnType) {
        returnType = sReturnType;
    }

    /**
     * Set the {@link DataFetcher} which will override the default {@link DataFetcher} for the field.
     *
     * @param dataFetcher the {@link DataFetcher}
     */
    public void dataFetcher(DataFetcher dataFetcher) {
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
    public int arrayLevels() {
        return arrayLevels;
    }

    /**
     * Return the format for a number or date.
     *
     * @return the format for a number or date
     */
    public String[] format() {
        if (format == null) {
            return null;
        }
        String[] copy = new String[format.length];
        System.arraycopy(format, 0, copy, 0, copy.length);
        return copy;
    }

    /**
     * Set the format for a number or date.
     *
     * @param format the format for a number or date
     */
    public void format(String[] format) {
        if (format == null) {
            this.format = null;
        } else {
            this.format = new String[format.length];
            System.arraycopy(format, 0, this.format, 0, this.format.length);
        }
    }

    /**
     * Return the default value for this field definition.
     *
     * @return the default value for this field definition
     */
    public Object defaultValue() {
        return defaultValue;
    }

    /**
     * Set the default value for this field definition.
     *
     * @param defaultValue the default value for this field definition
     */
    public void defaultValue(Object defaultValue) {
        this.defaultValue = defaultValue;
    }

    /**
     * Sets the original return type.
     *
     * @param originalType the original return type
     */
    public void originalType(Class<?> originalType) {
        this.originalType = originalType;
    }

    /**
     * Retrieve the original return type.
     *
     * @return the original return type
     */
    public Class<?> originalType() {
        return originalType;
    }

    /**
     * Sets the original array type.
     *
     * @param originalArrayType the original array type
     */
    public void originalArrayType(Class<?> originalArrayType) {
        this.originalArrayType = originalArrayType;
    }

    /**
     * Return the original array type.
     *
     * @return the original array type
     */
    public Class<?> originalArrayType() {
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
    public void arrayReturnTypeMandatory(boolean arrayReturnTypeMandatory) {
        isArrayReturnTypeMandatory = arrayReturnTypeMandatory;
    }

    /**
     * Set if the field has a default format applied.
     * @param defaultFormatApplied if the field has a default format applied
     */
    public void defaultFormatApplied(boolean defaultFormatApplied) {
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
    public void jsonbFormat(boolean isJsonbFormat) {
        this.isJsonbFormat = isJsonbFormat;
    }

    /**
     * Return true if the format is of type Jsonb.
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
    public void jsonbProperty(boolean isJsonbProperty) {
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
                + ", description='" + description() + '\'' + '}';
    }


    /**
     * A fluent API {@link io.helidon.common.Builder} to build instances of {@link SchemaFieldDefinition}.
     */
    public static class Builder implements io.helidon.common.Builder<SchemaFieldDefinition> {
        private String name;
        private String returnType;
        private boolean isArrayReturnType;
        private int arrayLevels;
        private boolean isReturnTypeMandatory;
        private List<SchemaArgument> listSchemaArguments = new ArrayList<>();
        private boolean isArrayReturnTypeMandatory;
        private DataFetcher dataFetcher;
        private Class<?> originalType;
        private Class<?> originalArrayType;
        private String[] format;
        private Object defaultValue;
        private boolean defaultFormatApplied;
        private boolean isJsonbFormat;
        private boolean isJsonbProperty;
        private String description;

        /**
         * Set the name of the {@link SchemaFieldDefinition}.
         *
         * @param name the name of the {@link SchemaFieldDefinition}
         * @return updated builder instance
         */
        public Builder name(String name) {
            this.name = name;
            return this;
        }

        /**
         * Set the returnType.
         *
         * @param returnType the returnType
         * @return updated builder instance
         */
        public Builder returnType(String returnType) {
            this.returnType = returnType;
            return this;
        }

        /**
         * Set if the return type is an array type such as a native array([]) or a List, Collection.
         *
         * @param isArrayReturnType true if the return type is an array type
         * @return updated builder instance
         */
        public Builder arrayReturnType(boolean isArrayReturnType) {
            this.isArrayReturnType = isArrayReturnType;
            return this;
        }

        /**
         * Set if the return type is mandatory.
         *
         * @param isReturnTypeMandatory true if the return type is mandatory.
         * @return updated builder instance
         */
        public Builder returnTypeMandatory(boolean isReturnTypeMandatory) {
            this.isReturnTypeMandatory = isReturnTypeMandatory;
            return this;
        }

        /**
         * Set the number of array levels if return type is an array.
         *
         * @param arrayLevels the number of array levels if return type is an array
         * @return updated builder instance
         */
        public Builder arrayLevels(int arrayLevels) {
            this.arrayLevels = arrayLevels;
            return this;
        }

        /**
         * Add an argument to the {@link SchemaFieldDefinition}.
         *
         * @param argument the argument to add to the {@link SchemaFieldDefinition}
         * @return updated builder instance
         */
        public Builder addArgument(SchemaArgument argument) {
            listSchemaArguments.add(argument);
            return this;
        }

        /**
         * Set if the value of the array is mandatory.
         *
         * @param isArrayReturnTypeMandatory If the return type is an array then indicates if the value in the
         *                                   array is mandatory
         * @return updated builder instance
         */
        public Builder arrayReturnTypeMandatory(boolean isArrayReturnTypeMandatory) {
            this.isArrayReturnTypeMandatory = isArrayReturnTypeMandatory;
            return this;
        }

        /**
         * Set the {@link DataFetcher} to override default behaviour of field.
         * @param dataFetcher {@link DataFetcher} to override default behaviour of field
         * @return updated builder instance
         */
        public Builder dataFetcher(DataFetcher dataFetcher) {
            this.dataFetcher = dataFetcher;
            return this;
        }

        /**
         * Set the original return type.
         * @param originalType  the original return type
         * @return updated builder instance
         */
        public Builder originalType(Class<?> originalType) {
            this.originalType = originalType;
            return this;
        }

        /**
         * Set the original array inner type if it is array type.
         * @param originalArrayType  the  original array inner type if it is array type
         * @return updated builder instance
         */
        public Builder originalArrayType(Class<?> originalArrayType) {
            this.originalArrayType = originalArrayType;
            return this;
        }

        /**
         * Set the format for a number or date.
         * @param format the format for a number or date
         * @return updated builder instance
         */
        public Builder format(String[] format) {
            if (format == null) {
                this.format = null;
            } else {
                this.format = new String[format.length];
                System.arraycopy(format, 0, this.format, 0, this.format.length);
            }

            return this;
        }

        /**
         * Set the default value for this field definition. Only valid for field definitions of an input type.
         * @param defaultValue the default value for this field definition
         * @return updated builder instance
         */
        public Builder defaultValue(Object defaultValue) {
            this.defaultValue = defaultValue;
            return this;
        }

        /**
         * Set if the field has a default format applied.
         * @param defaultFormatApplied true if the field has a default format applied
         * @return updated builder instance
         */
        public Builder defaultFormatApplied(boolean defaultFormatApplied) {
            this.defaultFormatApplied = defaultFormatApplied;
            return this;
        }

        /**
         * Set if the format is of type Jsonb.
         * @param isJsonbFormat if the format is of type Jsonb.
         * @return updated builder instance
         */
        public Builder jsonbFormat(boolean isJsonbFormat) {
            this.isJsonbFormat = isJsonbFormat;
            return this;
        }
        /**
         * Set if the property name is of type Jsonb.
         * @param isJsonbProperty if the property name is of type Jsonb.
         * @return updated builder instance
         */
        public Builder jsonbProperty(boolean isJsonbProperty) {
            this.isJsonbProperty = isJsonbProperty;
            return this;
        }

        /**
         * Set the description.
         * @param description the description of the {@link SchemaFieldDefinition}
         * @return updated builder instance
         */
        public Builder description(String description) {
            this.description = description;
            return this;
        }

        @Override
        public SchemaFieldDefinition build() {
            Objects.requireNonNull(name, "Name must be specified");
            return new SchemaFieldDefinition(this);
        }
    }
}
