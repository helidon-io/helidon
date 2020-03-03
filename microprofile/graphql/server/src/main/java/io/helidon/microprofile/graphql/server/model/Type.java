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
import java.util.Objects;

/**
 * The representation of a GraphQL Type.
 */
public class Type extends AbstractDescriptiveElement
        implements SchemaGenerator {

    /**
     * Name of the type.
     */
    private final String name;

    /**
     * Key class name.
     */
    private String keyClassName;

    /**
     * Value class name.
     */
    private final String valueClassName;

    /**
     * Indicates if the {@link Type} is an interface.
     */
    private boolean isInterface;

    /**
     * The interface that this {@link Type} implements.
     */
    private String implementingInterface;

    /**
     * {@link List} of {@link FieldDefinition}.
     */
    private final List<FieldDefinition> listFieldDefinitions;

    /**
     * Construct a @{link Type} with the given arguments.
     *
     * @param name           name of the Type
     * @param keyClassName   key class name
     * @param valueClassName value class name
     */
    public Type(String name, String keyClassName, String valueClassName) {
        this.name = name;
        this.keyClassName = keyClassName;
        this.valueClassName = valueClassName;
        this.listFieldDefinitions = new ArrayList<>();
    }

    /**
     * Return the GraphQL schema representation of the element.
     *
     * @return the GraphQL schema representation of the element.
     */
    @Override
    public String getSchemaAsString() {
        StringBuilder sb = new StringBuilder(getSchemaElementDescription())
                .append(getGraphQLName())
                .append(SPACER)
                .append(getName());

        if (implementingInterface != null) {
            sb.append(" implements " + implementingInterface);
        }
        sb.append(SPACER).append(OPEN_CURLY).append(NEWLINE);

        listFieldDefinitions.forEach(fd -> sb.append(fd.getSchemaAsString()).append(NEWLINE));

        // for each
        sb.append(CLOSE_CURLY).append(NEWLINE);

        return sb.toString();
    }

    /**
     * Generates a {@link InputType} from the current {@link Type}.
     *
     * @param sSuffix the suffix to add to the type as it must be unique within Type and InputType
     * @return an new {@link InputType}
     */
    public InputType createInputType(String sSuffix) {
        InputType inputType = new InputType(getName() + sSuffix, getKeyClassName(), getValueClassName());
        getFieldDefinitions().forEach(fd -> {
            fd.getArguments().clear();
            inputType.addFieldDefinition(fd);
        });
        return inputType;
    }

    /**
     * Set the key class name for the @{link Type}.
     *
     * @param keyClassName the key class name for the @{link Type}
     */
    public void setKeyClassName(String keyClassName) {
        this.keyClassName = keyClassName;
    }

    /**
     * Set if the {@link Type} is an interface.
     *
     * @param isInterface indicates if the {@link Type} is an interface;
     */
    public void setIsInterface(boolean isInterface) {
        this.isInterface = isInterface;
    }

    /**
     * Return the name of the {@link Type}.
     *
     * @return the name of the {@link Type}
     */
    public String getName() {
        return name;
    }

    /**
     * Return the key class name for the @{link Type}.
     *
     * @return the key class name for the @{link Type}.
     */
    public String getKeyClassName() {
        return keyClassName;
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
     * Return the {@link List} of {@link FieldDefinition}s.
     *
     * @return the {@link List} of {@link FieldDefinition}s
     */
    public List<FieldDefinition> getFieldDefinitions() {
        return listFieldDefinitions;
    }

    /**
     * Indicates if the {@link Type} is an interface.
     *
     * @return if the {@link Type} is an interface.
     */
    public boolean isInterface() {
        return isInterface;
    }

    /**
     * Return the interface that this {@link Type} implements.
     *
     * @return the interface that this {@link Type} implements
     */
    public String getImplementingInterface() {
        return implementingInterface;
    }

    /**
     * Set the interface that this {@link Type} implements.
     *
     * @param implementingInterface the interface that this {@link Type} implements
     */
    public void setImplementingInterface(String implementingInterface) {
        this.implementingInterface = implementingInterface;
    }

    /**
     * Add a {@link FieldDefinition} to the {@link Type}.
     *
     * @param fieldDefinition {@link FieldDefinition}
     */
    public void addFieldDefinition(FieldDefinition fieldDefinition) {
        listFieldDefinitions.add(fieldDefinition);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Type type = (Type) o;
        return isInterface == type.isInterface
                && Objects.equals(name, type.name)
                && Objects.equals(keyClassName, type.keyClassName)
                && Objects.equals(valueClassName, type.valueClassName)
                && Objects.equals(implementingInterface, type.implementingInterface)
                && Objects.equals(listFieldDefinitions, type.listFieldDefinitions)
                && Objects.equals(description, type.description);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(name,
                                  keyClassName,
                                  valueClassName,
                                  isInterface,
                                  implementingInterface,
                                  description,
                                  listFieldDefinitions);
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
                + ", keyClassName='" + keyClassName + '\''
                + ", valueClassName='" + valueClassName + '\''
                + ", isInterface='" + isInterface + '\''
                + ", description='" + description + '\''
                + ", implementingInterface='" + implementingInterface + '\''
                + ", listFieldDefinitions=" + listFieldDefinitions + '}';
    }

    /**
     * Return the GraphQL name for this {@link Type}.
     *
     * @return the GraphQL name for this {@link Type}.
     */
    protected String getGraphQLName() {
        return isInterface() ? "interface" : "type";
    }

}
