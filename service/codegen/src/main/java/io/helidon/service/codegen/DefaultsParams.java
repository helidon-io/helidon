/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.service.codegen;

import java.util.Objects;
import java.util.Optional;

import io.helidon.builder.api.Prototype;
import io.helidon.common.Generated;

/**
 * Parameters to code generate default values.
 *
 * @see #builder()
 * @see #create()
 */
@Generated(value = "io.helidon.builder.codegen.BuilderCodegen", trigger = "io.helidon.common.types.DefaultsParamsBlueprint")
public interface DefaultsParams extends DefaultsParamsBlueprint, Prototype.Api {

    /**
     * Create a new fluent API builder to customize configuration.
     *
     * @return a new builder
     */
    static DefaultsParams.Builder builder() {
        return new DefaultsParams.Builder();
    }

    /**
     * Create a new fluent API builder from an existing instance.
     *
     * @param instance an existing instance used as a base for the builder
     * @return a builder based on an instance
     */
    static DefaultsParams.Builder builder(DefaultsParams instance) {
        return DefaultsParams.builder().from(instance);
    }

    /**
     * Create a new instance with default values.
     *
     * @return a new instance
     */
    static DefaultsParams create() {
        return DefaultsParams.builder().buildPrototype();
    }

    /**
     * Fluent API builder base for {@link DefaultsParams}.
     *
     * @param <BUILDER>   type of the builder extending this abstract builder
     * @param <PROTOTYPE> type of the prototype interface that would be built by {@link #buildPrototype()}
     */
    abstract class BuilderBase<BUILDER extends DefaultsParams.BuilderBase<BUILDER, PROTOTYPE>, PROTOTYPE extends DefaultsParams>
            implements Prototype.Builder<BUILDER, PROTOTYPE> {

        private String contextField;
        private String mapperQualifier = "defaults";
        private String mappersField = "mappers";
        private String name = "default";

        /**
         * Protected to support extensibility.
         */
        protected BuilderBase() {
        }

        /**
         * Update this builder from an existing prototype instance. This method disables automatic service discovery.
         *
         * @param prototype existing prototype to update this builder from
         * @return updated builder instance
         */
        public BUILDER from(DefaultsParams prototype) {
            mapperQualifier(prototype.mapperQualifier());
            mappersField(prototype.mappersField());
            name(prototype.name());
            contextField(prototype.contextField());
            return self();
        }

        /**
         * Qualifier used when mapping string values to target type.
         *
         * @param mapperQualifier mapper qualifier
         * @return updated builder instance
         * @see #mapperQualifier()
         */
        public BUILDER mapperQualifier(String mapperQualifier) {
            Objects.requireNonNull(mapperQualifier);
            this.mapperQualifier = mapperQualifier;
            return self();
        }

        /**
         * Name of the field/variable that contains a {@code Mappers} instance.
         *
         * @param mappersField mappers field name
         * @return updated builder instance
         * @see #mappersField()
         */
        public BUILDER mappersField(String mappersField) {
            Objects.requireNonNull(mappersField);
            this.mappersField = mappersField;
            return self();
        }

        /**
         * Name as sent to default value provider.
         *
         * @param name name to use with provider
         * @return updated builder instance
         * @see #name()
         */
        public BUILDER name(String name) {
            Objects.requireNonNull(name);
            this.name = name;
            return self();
        }

        /**
         * Clear existing value of this property.
         *
         * @return updated builder instance
         * @see #contextField()
         */
        public BUILDER clearContextField() {
            this.contextField = null;
            return self();
        }

        /**
         * Name of the field/variable of the context to be sent to default value provider.
         *
         * @param contextField context name
         * @return updated builder instance
         * @see #contextField()
         */
        public BUILDER contextField(String contextField) {
            Objects.requireNonNull(contextField);
            this.contextField = contextField;
            return self();
        }

        /**
         * Qualifier used when mapping string values to target type.
         *
         * @return the mapper qualifier
         */
        public String mapperQualifier() {
            return mapperQualifier;
        }

        /**
         * Name of the field/variable that contains a {@code Mappers} instance.
         *
         * @return the mappers field
         */
        public String mappersField() {
            return mappersField;
        }

        /**
         * Name as sent to default value provider.
         *
         * @return the name
         */
        public String name() {
            return name;
        }

        /**
         * Name of the field/variable of the context to be sent to default value provider.
         *
         * @return the context field
         */
        public Optional<String> contextField() {
            return Optional.ofNullable(contextField);
        }

        @Override
        public String toString() {
            return "DefaultsParamsBuilder{"
                    + "mapperQualifier=" + mapperQualifier + ","
                    + "mappersField=" + mappersField + ","
                    + "name=" + name + ","
                    + "contextField=" + contextField
                    + "}";
        }

        /**
         * Handles providers and decorators.
         */
        protected void preBuildPrototype() {
        }

        /**
         * Validates required properties.
         */
        protected void validatePrototype() {
        }

        /**
         * Name of the field/variable of the context to be sent to default value provider.
         *
         * @param contextField context name
         * @return updated builder instance
         * @see #contextField()
         */
        BUILDER contextField(Optional<String> contextField) {
            Objects.requireNonNull(contextField);
            this.contextField = contextField.map(java.lang.String.class::cast).orElse(this.contextField);
            return self();
        }

        /**
         * Generated implementation of the prototype, can be extended by descendant prototype implementations.
         */
        protected static class DefaultsParamsImpl implements DefaultsParams {

            private final Optional<String> contextField;
            private final String mapperQualifier;
            private final String mappersField;
            private final String name;

            /**
             * Create an instance providing a builder.
             *
             * @param builder extending builder base of this prototype
             */
            protected DefaultsParamsImpl(DefaultsParams.BuilderBase<?, ?> builder) {
                this.mapperQualifier = builder.mapperQualifier();
                this.mappersField = builder.mappersField();
                this.name = builder.name();
                this.contextField = builder.contextField();
            }

            @Override
            public String mapperQualifier() {
                return mapperQualifier;
            }

            @Override
            public String mappersField() {
                return mappersField;
            }

            @Override
            public String name() {
                return name;
            }

            @Override
            public Optional<String> contextField() {
                return contextField;
            }

            @Override
            public String toString() {
                return "DefaultsParams{"
                        + "mapperQualifier=" + mapperQualifier + ","
                        + "mappersField=" + mappersField + ","
                        + "name=" + name + ","
                        + "contextField=" + contextField
                        + "}";
            }

            @Override
            public boolean equals(Object o) {
                if (o == this) {
                    return true;
                }
                if (!(o instanceof DefaultsParams other)) {
                    return false;
                }
                return Objects.equals(mapperQualifier, other.mapperQualifier())
                        && Objects.equals(mappersField, other.mappersField())
                        && Objects.equals(name, other.name())
                        && Objects.equals(contextField, other.contextField());
            }

            @Override
            public int hashCode() {
                return Objects.hash(mapperQualifier, mappersField, name, contextField);
            }

        }

    }

    /**
     * Fluent API builder for {@link DefaultsParams}.
     */
    class Builder extends DefaultsParams.BuilderBase<DefaultsParams.Builder, DefaultsParams>
            implements io.helidon.common.Builder<DefaultsParams.Builder, DefaultsParams> {

        private Builder() {
        }

        @Override
        public DefaultsParams buildPrototype() {
            preBuildPrototype();
            validatePrototype();
            return new DefaultsParamsImpl(this);
        }

        @Override
        public DefaultsParams build() {
            return buildPrototype();
        }

    }

}
