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

import java.util.Objects;


/**
 * The representation of a GraphQL Argument or Parameter.
 */
public class SchemaArgument
        extends AbstractDescriptiveElement
        implements ElementGenerator {
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
                .append(COLON)
                .append(SPACER)
                .append(getArgumentType());

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

    @Override
    public String toString() {
        return "Argument{"
                + "argumentName='" + argumentName + '\''
                + ", argumentType='" + argumentType + '\''
                + ", isMandatory=" + isMandatory
                + ", defaultValue=" + defaultValue
                + ", originalType=" + originalType
                + ", sourceArgument=" + sourceArgument
                + ", format=" + format
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
                && Objects.equals(getDescription(), schemaArgument.getDescription())
                && Objects.equals(defaultValue, schemaArgument.defaultValue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), argumentName, argumentType, sourceArgument,
                            isMandatory, defaultValue, getDescription(), originalType, format);
    }
}
