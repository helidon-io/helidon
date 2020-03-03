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

import java.util.Objects;

/**
 * The representation of a GraphQL Argument or Parameter.
 */
public class Argument extends AbstractDescriptiveElement
        implements SchemaGenerator {
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
    private final Object defaultValue;

    /**
     * Construct a {@link Argument} instance.
     *
     * @param argumentName name of the argument
     * @param argumentType type of the argument
     * @param isMandatory  indicates if the argument is mandatory
     */
    public Argument(String argumentName, String argumentType, boolean isMandatory, Object defaultValue) {
        this.argumentName = argumentName;
        this.argumentType = argumentType;
        this.isMandatory = isMandatory;
        this.defaultValue = defaultValue;
    }

    /**
     * Return the GraphQL schema representation of the element.
     *
     * @return the GraphQL schema representation of the element.
     */
    @Override
    public String getSchemaAsString() {
        StringBuilder sb = new StringBuilder(getSchemaElementDescription())
                .append(getArgumentName())
                .append(COLON)
                .append(SPACER)
                .append(getArgumentType());

        if (isMandatory) {
            sb.append(MANDATORY);
        }

        if (defaultValue != null) {
            sb.append(SPACER)
                    .append(EQUALS)
                    .append(SPACER);
            boolean isString = defaultValue instanceof String;
            if (isString) {
                sb.append(QUOTE)
                        .append(defaultValue)
                        .append(QUOTE);
            } else {
                sb.append(defaultValue);
            }
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

    @Override
    public String toString() {
        return "Argument{" +
                "argumentName='" + argumentName + '\'' +
                ", argumentType='" + argumentType + '\'' +
                ", isMandatory=" + isMandatory +
                ", defaultValue=" + defaultValue +
                ", description='" + description + '\'' +
                '}';
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
        Argument argument = (Argument) o;
        return isMandatory == argument.isMandatory &&
                Objects.equals(argumentName, argument.argumentName) &&
                Objects.equals(argumentType, argument.argumentType) &&
                Objects.equals(description, argument.description) &&
                Objects.equals(defaultValue, argument.defaultValue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), argumentName, argumentType, isMandatory, defaultValue, description);
    }
}
