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

import java.util.Arrays;
import java.util.Objects;

/**
 * The representation of a GraphQL Argument or Parameter.
 */
public class SchemaArgument extends AbstractDescriptiveElement implements ElementGenerator {
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
     * Construct a {@link SchemaArgument} instance.
     *
     * @param argumentName name of the argument
     * @param argumentType type of the argument
     * @param isMandatory  indicates if the argument is mandatory
     * @param defaultValue default value for the argument
     * @param originalType original argument type before it was converted to a GraphQL representation.
     */
    public SchemaArgument(String argumentName, String argumentType,
                          boolean isMandatory, Object defaultValue, Class<?> originalType) {
        this.argumentName = argumentName;
        this.argumentType = argumentType;
        this.isMandatory = isMandatory;
        this.defaultValue = defaultValue;
        this.originalType = originalType;
    }

    /**
     * Return the GraphQL schema representation of the element.
     *
     * @return the GraphQL schema representation of the element.
     */
    @Override
    public String getSchemaAsString() {
        StringBuilder sb = new StringBuilder(getSchemaElementDescription(getFormat()))
                .append(getArgumentName())
                .append(COLON);

         if (isArrayReturnType()) {
            int count = getArrayLevels();
            sb.append(SPACER).append(repeat(count, OPEN_SQUARE))
                    .append(getArgumentType())
                    .append(isArrayReturnTypeMandatory() ? MANDATORY : NOTHING)
                    .append(repeat(count, CLOSE_SQUARE));
        } else {
            sb.append(SPACER).append(getArgumentType());
        }

        if (isMandatory) {
            sb.append(MANDATORY);
        }

        if (defaultValue != null) {
            sb.append(generateDefaultValue(defaultValue, getArgumentType()));
        }

        return sb.toString();
    }

    /**
     * Return the argument name.
     *
     * @return the argument name
     */
    public String getArgumentName() {
        return argumentName;
    }

    /**
     * Return the argument type.
     *
     * @return the argument type
     */
    public String getArgumentType() {
        return argumentType;
    }

    /**
     * Indicates if the argument is mandatory.
     *
     * @return indicates if the argument is mandatory
     */
    public boolean isMandatory() {
        return isMandatory;
    }

    /**
     * Set the type of the argument.
     *
     * @param argumentType the type of the argument
     */
    public void setArgumentType(String argumentType) {
        this.argumentType = argumentType;
    }

    /**
     * Return the default value for this argument.
     *
     * @return the default value for this argument
     */
    public Object getDefaultValue() {
        return defaultValue;
    }

    /**
     * Set the default value for this argument.
     *
     * @param defaultValue the default value for this argument
     */
    public void setDefaultValue(Object defaultValue) {
        this.defaultValue = defaultValue;
    }

    /**
     * Retrieve the original argument type.
     *
     * @return the original argument type
     */
    public Class<?> getOriginalType() {
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
    public void setSourceArgument(boolean sourceArgument) {
        this.sourceArgument = sourceArgument;
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
     * Set the number of array levels if return type is an array.
     *
     * @param arrayLevels the number of array levels if return type is an array
     */
    public void setArrayLevels(int arrayLevels) {
       this.arrayLevels = arrayLevels;
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
     * Set if the return type is an array type.
     * @param isArrayReturnType if the return type is an array type
     */
    public void setArrayReturnType(boolean isArrayReturnType) {
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
    public void setArrayReturnTypeMandatory(boolean arrayReturnTypeMandatory) {
        isArrayReturnTypeMandatory = arrayReturnTypeMandatory;
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
                + ", description='" + getDescription() + '\'' + '}';
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
                && Objects.equals(format, schemaArgument.format)
                && Objects.equals(sourceArgument, schemaArgument.sourceArgument)
                && Objects.equals(originalArrayType, schemaArgument.originalArrayType)
                && Objects.equals(getDescription(), schemaArgument.getDescription())
                && Objects.equals(defaultValue, schemaArgument.defaultValue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), argumentName, argumentType, sourceArgument,
                            isMandatory, defaultValue, getDescription(), originalType, format, originalArrayType);
    }
}
