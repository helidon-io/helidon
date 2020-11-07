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
class SchemaType extends AbstractDescriptiveElement implements ElementGenerator {

    /**
     * Name of the type.
     */
    private String name;

    /**
     * Value class name.
     */
    private String valueClassName;

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
    private List<SchemaFieldDefinition> listSchemaFieldDefinitions;

    /**
     * Private no-args constructor only use by subclass {@link SchemaInputType}.
     * @param name  name of the type
     * @param valueClassName  value class name
     */
    protected SchemaType(String name, String valueClassName) {
        this.name = name;
        this.valueClassName = valueClassName;
        this.listSchemaFieldDefinitions = new ArrayList<>();
    }

    /**
     * Construct a {@link SchemaType}.
     *
     * @param builder the {@link Builder} to construct from
     */
    private SchemaType(Builder builder) {
        this.name = builder.name;
        this.valueClassName = builder.valueClassName;
        this.isInterface = builder.isInterface;
        this.implementingInterface = builder.implementingInterface;
        this.listSchemaFieldDefinitions = builder.listSchemaFieldDefinitions;
        description(builder.description);
    }

    /**
     * Fluent API builder to create {@link SchemaType}.
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
        StringBuilder sb = new StringBuilder(getSchemaElementDescription(null))
                .append(getGraphQLName())
                .append(SPACER)
                .append(name());

        if (implementingInterface != null) {
            sb.append(" implements ").append(implementingInterface);
        }
        sb.append(SPACER).append(OPEN_CURLY).append(NEWLINE);

        listSchemaFieldDefinitions.forEach(fd -> sb.append(fd.getSchemaAsString()).append(NEWLINE));

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
        SchemaInputType inputType = new SchemaInputType(name() + sSuffix, valueClassName());
        fieldDefinitions().forEach(fd -> {
            fd.arguments().clear();
            inputType.addFieldDefinition(fd);
        });
        return inputType;
    }

    /**
     * Set if the {@link SchemaType} is an interface.
     *
     * @param isInterface indicates if the {@link SchemaType} is an interface;
     */
    public void isInterface(boolean isInterface) {
        this.isInterface = isInterface;
    }

    /**
     * Return the name of the {@link SchemaType}.
     *
     * @return the name of the {@link SchemaType}
     */
    public String name() {
        return name;
    }

    /**
     * Set the name of the {@link SchemaType}.
     *
     * @param name the name of the {@link SchemaType}
     */
    public void name(String name) {
        this.name = name;
    }

    /**
     * Return the value class name for the @{link Type}.
     *
     * @return the value class name for the @{link Type}.
     */
    public String valueClassName() {
        return valueClassName;
    }

    /**
     * Return the {@link List} of {@link SchemaFieldDefinition}s.
     *
     * @return the {@link List} of {@link SchemaFieldDefinition}s
     */
    public List<SchemaFieldDefinition> fieldDefinitions() {
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
    public String implementingInterface() {
        return implementingInterface;
    }

    /**
     * Set the interface that this {@link SchemaType} implements.
     *
     * @param implementingInterface the interface that this {@link SchemaType} implements
     */
    public void implementingInterface(String implementingInterface) {
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

    /**
     * Return a {@link SchemaFieldDefinition} that matches the name.
     *
     * @param fdName type name to match
     * @return a {@link SchemaType} that matches the type name or null if none found
     */
    public SchemaFieldDefinition getFieldDefinitionByName(String fdName) {
        for (SchemaFieldDefinition fieldDefinition : listSchemaFieldDefinitions) {
            if (fieldDefinition.name().equals(fdName)) {
                return fieldDefinition;
            }
        }
        return null;
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
                && Objects.equals(description(), schemaType.description());
    }

    @Override
    public int hashCode() {
        return Objects.hash(name,
                            valueClassName,
                            isInterface,
                            implementingInterface,
                            description(),
                            listSchemaFieldDefinitions);

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
                + ", description='" + description() + '\''
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

    /**
     * A fluent API {@link io.helidon.common.Builder} to build instances of {@link SchemaType}.
     */
    public static class Builder implements io.helidon.common.Builder<SchemaType> {

        private String name;
        private String valueClassName;
        private String description;
        private boolean isInterface;
        private String implementingInterface;
        private List<SchemaFieldDefinition> listSchemaFieldDefinitions = new ArrayList<>();

        /**
         * Set the name of the {@link SchemaType}.
         *
         * @param name the name of the {@link SchemaType}
         * @return updated builder instance
         */
        public Builder name(String name) {
            this.name = name;
            return this;
        }

        /**
         * Set the the value class name.
         * @param valueClassName the value class name
         * @return updated builder instance
         */
        public Builder valueClassName(String valueClassName) {
            this.valueClassName = valueClassName;
            return this;
        }

        /**
         * Set the description of the {@link SchemaType}.
         * @param description the description of the {@link SchemaType}
         * @return updated builder instance
         */
        public Builder description(String description) {
            this.description = description;
            return this;
        }

        /**
         * Set if the {@link SchemaType} is an interface.
         * @param isInterface true if the {@link SchemaType} is an interface
         * @return updated builder instance
         */
        public Builder isInterface(boolean isInterface) {
            this.isInterface = isInterface;
            return this;
        }

        /**
         * Set the interface that this {@link SchemaType} implements.
         * @param implementingInterface the interface that this {@link SchemaType} implements
         * @return updated builder instance
         */
        public Builder implementingInterface(String implementingInterface) {
            this.implementingInterface = implementingInterface;
            return this;
        }

        /**
         * Add a {@link SchemaFieldDefinition} to the {@link SchemaType}.
         * @param fieldDefinition {@link SchemaFieldDefinition} to add
         * @return updated builder instance
         */
        public Builder addFieldDefinition(SchemaFieldDefinition fieldDefinition) {
            this.listSchemaFieldDefinitions.add(fieldDefinition);
            return this;
        }

        @Override
        public SchemaType build() {
            Objects.requireNonNull(name, "Name must be specified");
            return new SchemaType(this);
        }
    }
}
