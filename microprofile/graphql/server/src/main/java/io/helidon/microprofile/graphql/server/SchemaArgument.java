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

import java.util.Arrays;
import java.util.Objects;

/**
 * The representation of a GraphQL Argument or Parameter.
 */
class SchemaArgument extends AbstractDescriptiveElement implements ElementGenerator {
    /**
     * Argument name.
     */
    private final String argumentName;

    /**
     * Argument type.
     */
    private String argumentType;

    /**
     * Indicates if the argument is mandatory.
     */
    private final boolean isMandatory;

    /**
     * The default value for this argument.
     */
    private Object defaultValue;

    /**
     * Original argument type before it was converted to a GraphQL representation.
     */
    private final Class<?> originalType;

    /**
     * Indicates if this argument is a source argument, which should be excluded from the query parameters.
     */
    private boolean sourceArgument;

    /**
     * Defines the format for a number or date.
     */
    private String[] format;

    /**
     * Indicates if the return type is an array type such as a native array([]) or a List, Collection, etc.
     */
    private boolean isArrayReturnType;

    /**
     * The number of array levels if return type is an array.
     */
    private int arrayLevels;

    /**
     * Indicates if the return type is mandatory.
     */
    private boolean isArrayReturnTypeMandatory;

    /**
     * Original array inner type if it is array type.
     */
    private Class<?> originalArrayType;

    /**
     * Construct a {@link SchemaArgument}.
     *
     * @param builder the {@link Builder} to construct from
     */
    private SchemaArgument(Builder builder) {
        this.argumentName = builder.argumentName;
        this.argumentType = builder.argumentType;
        this.isMandatory = builder.isMandatory;
        this.defaultValue = builder.defaultValue;
        this.originalType = builder.originalType;
        this.sourceArgument = builder.sourceArgument;
        this.format = builder.format;
        this.isArrayReturnType = builder.isArrayReturnType;
        this.arrayLevels = builder.arrayLevels;
        this.isArrayReturnTypeMandatory = builder.isArrayReturnTypeMandatory;
        this.originalArrayType = builder.originalArrayType;
        description(builder.description);
    }

    /**
     * Fluent API builder to create {@link SchemaArgument}.
     *
     * @return new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Return the GraphQL schema representation of the element.
     *
     * @return the GraphQL schema representation of the element.
     */
    @Override
    public String getSchemaAsString() {
        StringBuilder sb = new StringBuilder(getSchemaElementDescription(format()))
                .append(argumentName())
                .append(COLON);

         if (isArrayReturnType()) {
            int count = arrayLevels();
            sb.append(SPACER).append(repeat(count, OPEN_SQUARE))
                    .append(argumentType())
                    .append(isArrayReturnTypeMandatory() ? MANDATORY : NOTHING)
                    .append(repeat(count, CLOSE_SQUARE));
        } else {
            sb.append(SPACER).append(argumentType());
        }

        if (isMandatory) {
            sb.append(MANDATORY);
        }

        if (defaultValue != null) {
            sb.append(generateDefaultValue(defaultValue, argumentType()));
        }

        return sb.toString();
    }

    /**
     * Return the argument name.
     *
     * @return the argument name
     */
    public String argumentName() {
        return argumentName;
    }

    /**
     * Return the argument type.
     *
     * @return the argument type
     */
    public String argumentType() {
        return argumentType;
    }

    /**
     * Indicates if the argument is mandatory.
     *
     * @return indicates if the argument is mandatory
     */
    public boolean mandatory() {
        return isMandatory;
    }

    /**
     * Set the type of the argument.
     *
     * @param argumentType the type of the argument
     */
    public void argumentType(String argumentType) {
        this.argumentType = argumentType;
    }

    /**
     * Return the default value for this argument.
     *
     * @return the default value for this argument
     */
    public Object defaultValue() {
        return defaultValue;
    }

    /**
     * Set the default value for this argument.
     *
     * @param defaultValue the default value for this argument
     */
    public void defaultValue(Object defaultValue) {
        this.defaultValue = defaultValue;
    }

    /**
     * Retrieve the original argument type.
     *
     * @return the original argument type
     */
    public Class<?> originalType() {
        return originalType;
    }

    /**
     * Indicates if the argument is a source argument.
     *
     * @return if the argument is a source argument.
     */
    public boolean isSourceArgument() {
        return sourceArgument;
    }

    /**
     * Set if the argument is a source argument.
     *
     * @param sourceArgument if the argument is a source argument.
     */
    public void sourceArgument(boolean sourceArgument) {
        this.sourceArgument = sourceArgument;
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
     * Set the number of array levels if return type is an array.
     *
     * @param arrayLevels the number of array levels if return type is an array
     */
    public void arrayLevels(int arrayLevels) {
       this.arrayLevels = arrayLevels;
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
     * Set if the return type is an array type.
     * @param isArrayReturnType if the return type is an array type
     */
    public void arrayReturnType(boolean isArrayReturnType) {
        this.isArrayReturnType = isArrayReturnType;
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
     * Indicates if the array return type is mandatory.
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

    @Override
    public String toString() {
        return "Argument{"
                + "argumentName='" + argumentName + '\''
                + ", argumentType='" + argumentType + '\''
                + ", isMandatory=" + isMandatory
                + ", defaultValue=" + defaultValue
                + ", originalType=" + originalType
                + ", sourceArgument=" + sourceArgument
                + ", isReturnTypeMandatory=" + isArrayReturnTypeMandatory
                + ", isArrayReturnType=" + isArrayReturnType
                + ", originalArrayType=" + originalArrayType
                + ", arrayLevels=" + arrayLevels
                + ", format=" + Arrays.toString(format)
                + ", description='" + description() + '\'' + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        SchemaArgument schemaArgument = (SchemaArgument) o;
        return isMandatory == schemaArgument.isMandatory
                && Objects.equals(argumentName, schemaArgument.argumentName)
                && Objects.equals(argumentType, schemaArgument.argumentType)
                && Objects.equals(originalType, schemaArgument.originalType)
                && Arrays.equals(format, schemaArgument.format)
                && Objects.equals(sourceArgument, schemaArgument.sourceArgument)
                && Objects.equals(originalArrayType, schemaArgument.originalArrayType)
                && Objects.equals(description(), schemaArgument.description())
                && Objects.equals(defaultValue, schemaArgument.defaultValue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), argumentName, argumentType, sourceArgument,
                            isMandatory, defaultValue, description(), originalType, format, originalArrayType);
    }

    /**
     * A fluent API {@link io.helidon.common.Builder} to build instances of {@link SchemaArgument}.
     */
    public static class Builder implements io.helidon.common.Builder<SchemaArgument> {

        private String argumentName;
        private String argumentType;
        private String description;
        private boolean isMandatory;
        private Object defaultValue;
        private Class<?> originalType;
        private boolean sourceArgument;
        private String[] format;
        private boolean isArrayReturnType;
        private int arrayLevels;
        private boolean isArrayReturnTypeMandatory;
        private Class<?> originalArrayType;

        /**
         * Set the argument name.
         * @param argumentName the argument name
         * @return updated builder instance
         */
        public Builder argumentName(String argumentName) {
            this.argumentName = argumentName;
            return this;
        }

        /**
         * Set the argument type.
         *
         * @param argumentType the argument type
         * @return updated builder instance
         */
        public Builder argumentType(String argumentType) {
            this.argumentType = argumentType;
            return this;
        }

        /**
         * Set if the argument is mandatory.
         *
         * @param isMandatory true if the argument is mandatory
         * @return updated builder instance
         */
        public Builder mandatory(boolean isMandatory) {
            this.isMandatory = isMandatory;
            return this;
        }

        /**
         * Set the default value for this argument.
         * @param defaultValue the default value for this argument
         * @return updated builder instance
         */
        public Builder defaultValue(Object defaultValue) {
            this.defaultValue = defaultValue;
            return this;
        }

        /**
         * Set the description of the {@link SchemaArgument}.
         * @param description the description of the {@link SchemaArgument}
         * @return updated builder instance
         */
        public Builder description(String description) {
            this.description = description;
            return this;
        }

        /**
         * Set the original return type.
         *
         * @param originalType the original return type
         * @return updated builder instance
         */
        public Builder originalType(Class<?> originalType) {
            this.originalType = originalType;
            return this;
        }

        /**
         * Set if the argument is a source argument.
         * @param sourceArgument true if the argument is a source argument
         * @return updated builder instance
         */
        public Builder sourceArgument(boolean sourceArgument) {
            this.sourceArgument = sourceArgument;
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
         * Set the original array inner type if it is array type.
         * @param originalArrayType  the  original array inner type if it is array type
         * @return updated builder instance
         */
        public Builder originalArrayType(Class<?> originalArrayType) {
            this.originalArrayType = originalArrayType;
            return this;
        }

        /**
         * Build the instance from this builder.
         *
         * @return instance of the built type
         */
        @Override
        public SchemaArgument build() {
            Objects.requireNonNull(argumentName, "Argument name must be specified");
            Objects.requireNonNull(argumentType, "Argument type must be specified");
            return new SchemaArgument(this);
        }
    }
}
