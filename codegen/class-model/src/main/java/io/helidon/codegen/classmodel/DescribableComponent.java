/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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
package io.helidon.codegen.classmodel;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.helidon.common.types.TypeName;

abstract class DescribableComponent extends ModelComponent {

    private final Type type;
    private final List<String> description;

    DescribableComponent(Builder<?, ?> builder) {
        super(builder);
        this.type = builder.type;
        this.description = builder.description;
    }

    Type type() {
        return type;
    }

    /**
     * Description (javadoc) of this component.
     *
     * @return description lines
     */
    public List<String> description() {
        return description;
    }

    /**
     * Type name of this component.
     *
     * @return type name
     */
    public TypeName typeName() {
        return type().typeName();
    }


    @Override
    void addImports(ImportOrganizer.Builder imports) {
        if (includeImport() && type != null) {
            type.addImports(imports);
        }
    }

    abstract static class Builder<B extends Builder<B, T>, T extends DescribableComponent>
            extends ModelComponent.Builder<B, T> {

        private final List<String> description = new ArrayList<>();
        private Type type;

        /**
         * Set type of the component.
         * This should be fully qualified type name.
         *
         * @param type fully qualified type name
         * @return updated builder instance
         */
        B type(String type) {
            Objects.requireNonNull(type);
            return type(TypeName.create(type));
        }

        /**
         * Set type of the component.
         *
         * @param type type of the component
         * @return updated builder instance
         */
        B type(Class<?> type) {
            Objects.requireNonNull(type);
            return type(TypeName.create(type));
        }

        /**
         * Set type of the component.
         *
         * @param type type of the component
         * @return updated builder instance
         */
        B type(TypeName type) {
            Objects.requireNonNull(type);
            return type(Type.fromTypeName(type));
        }

        B type(Type type) {
            Objects.requireNonNull(type);
            this.type = type;
            return identity();
        }

        /**
         * Set description of the component.
         * It overwrites previously set description.
         *
         * @param description component description
         * @return updated builder instance
         */
        B description(String description) {
            Objects.requireNonNull(description);
            this.description.clear();
            this.description.add(description);
            return identity();
        }

        /**
         * Set description of the component.
         * It overwrites previously set description.
         *
         * @param description component description
         * @return updated builder instance
         */
        B description(List<String> description) {
            Objects.requireNonNull(description);
            this.description.clear();
            this.description.addAll(description);
            return identity();
        }

        Type type() {
            return type;
        }
    }

}
