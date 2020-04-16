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
import java.util.List;
import java.util.Objects;

/**
 * The representation of a GraphQL Type.
 */
public class SchemaType
        extends AbstractDescriptiveElement
        implements ElementGenerator {

    /**
     * Name of the type.
     */
    private String name;

    /**
     * Value class name.
     */
    private final String valueClassName;

    /**
     * Indicates if the {@link SchemaType} is an interface.
     */
    private boolean isInterface;

    /**
     * The interface that this {@link SchemaType} implements.
     */
    private String implementingInterface;

    /**
     * {@link List} of {@link SchemaFieldDefinition}.
     */
    private final List<SchemaFieldDefinition> listSchemaFieldDefinitions;

    /**
     * Construct a @{link Type} with the given arguments.
     *
     * @param name           name of the Type
     * @param valueClassName value class name
     */
    public SchemaType(String name, String valueClassName) {
        this.name = name;
        this.valueClassName = valueClassName;
        this.listSchemaFieldDefinitions = new ArrayList<>();
    }

    /**
     * Return the GraphQL schema representation of the element.
     *
     * @return the GraphQL schema representation of the element.
     */
    @Override
    public String getSchemaAsString() {
        StringBuilder sb = new StringBuilder(getSchemaElementDescription(null))
                .append(getGraphQLName())
                .append(SPACER)
                .append(getName());

        if (implementingInterface != null) {
            sb.append(" implements " + implementingInterface);
        }
        sb.append(SPACER).append(OPEN_CURLY).append(NEWLINE);

        listSchemaFieldDefinitions.forEach(fd -> sb.append(fd.getSchemaAsString()).append(NEWLINE));

        // for each
        sb.append(CLOSE_CURLY).append(NEWLINE);

        return sb.toString();
    }

    /**
     * Generates a {@link SchemaInputType} from the current {@link SchemaType}.
     *
     * @param sSuffix the suffix to add to the type as it must be unique within Type and InputType
     * @return an new {@link SchemaInputType}
     */
    public SchemaInputType createInputType(String sSuffix) {
        SchemaInputType inputType = new SchemaInputType(getName() + sSuffix, getValueClassName());
        getFieldDefinitions().forEach(fd -> {
            fd.getArguments().clear();
            inputType.addFieldDefinition(fd);
        });
        return inputType;
    }

    /**
     * Set if the {@link SchemaType} is an interface.
     *
     * @param isInterface indicates if the {@link SchemaType} is an interface;
     */
    public void setIsInterface(boolean isInterface) {
        this.isInterface = isInterface;
    }

    /**
     * Return the name of the {@link SchemaType}.
     *
     * @return the name of the {@link SchemaType}
     */
    public String getName() {
        return name;
    }

    /**
     * Set the name of the {@link SchemaType}.
     * @param name the name of the {@link SchemaType}
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Return the value class name for the @{link Type}.
     *
     * @return the value class name for the @{link Type}.
     */
    public String getValueClassName() {
        return valueClassName;
    }

    /**
     * Return the {@link List} of {@link SchemaFieldDefinition}s.
     *
     * @return the {@link List} of {@link SchemaFieldDefinition}s
     */
    public List<SchemaFieldDefinition> getFieldDefinitions() {
        return listSchemaFieldDefinitions;
    }

    /**
     * Indicates if the {@link SchemaType} is an interface.
     *
     * @return if the {@link SchemaType} is an interface.
     */
    public boolean isInterface() {
        return isInterface;
    }

    /**
     * Return the interface that this {@link SchemaType} implements.
     *
     * @return the interface that this {@link SchemaType} implements
     */
    public String getImplementingInterface() {
        return implementingInterface;
    }

    /**
     * Set the interface that this {@link SchemaType} implements.
     *
     * @param implementingInterface the interface that this {@link SchemaType} implements
     */
    public void setImplementingInterface(String implementingInterface) {
        this.implementingInterface = implementingInterface;
    }

    /**
     * Add a {@link SchemaFieldDefinition} to the {@link SchemaType}.
     *
     * @param schemaFieldDefinition {@link SchemaFieldDefinition}
     */
    public void addFieldDefinition(SchemaFieldDefinition schemaFieldDefinition) {
        listSchemaFieldDefinitions.add(schemaFieldDefinition);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SchemaType schemaType = (SchemaType) o;
        return isInterface == schemaType.isInterface
                && Objects.equals(name, schemaType.name)
                && Objects.equals(valueClassName, schemaType.valueClassName)
                && Objects.equals(implementingInterface, schemaType.implementingInterface)
                && Objects.equals(listSchemaFieldDefinitions, schemaType.listSchemaFieldDefinitions)
                && Objects.equals(getDescription(), schemaType.getDescription());
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(name,
                                  valueClassName,
                                  isInterface,
                                  implementingInterface,
                                  getDescription(),
                                  listSchemaFieldDefinitions);
        return result;
    }

    @Override
    public String toString() {
        return "Type" + toStringInternal();
    }

    /**
     * Internal toString() used by sub-type.
     *
     * @return internal toString()
     */
    protected String toStringInternal() {
        return "{"
                + "name='" + name + '\''
                + ", valueClassName='" + valueClassName + '\''
                + ", isInterface='" + isInterface + '\''
                + ", description='" + getDescription() + '\''
                + ", implementingInterface='" + implementingInterface + '\''
                + ", listFieldDefinitions=" + listSchemaFieldDefinitions + '}';
    }

    /**
     * Return the GraphQL name for this {@link SchemaType}.
     *
     * @return the GraphQL name for this {@link SchemaType}.
     */
    protected String getGraphQLName() {
        return isInterface() ? "interface" : "type";
    }

}
