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

package io.helidon.common.types;

import java.util.Objects;
import java.util.Optional;

import io.helidon.builder.api.Prototype;
import io.helidon.common.Errors;

/**
 * A requires directive of a module info.
 *
 * @see #builder()
 * @see #create()
 */
public interface ModuleInfoRequires extends ModuleInfoRequiresBlueprint, Prototype.Api {

    /**
     * Create a new fluent API builder to customize configuration.
     *
     * @return a new builder
     */
    static ModuleInfoRequires.Builder builder() {
        return new ModuleInfoRequires.Builder();
    }

    /**
     * Create a new fluent API builder from an existing instance.
     *
     * @param instance an existing instance used as a base for the builder
     * @return a builder based on an instance
     */
    static ModuleInfoRequires.Builder builder(ModuleInfoRequires instance) {
        return ModuleInfoRequires.builder().from(instance);
    }

    /**
     * Create a new instance with default values.
     *
     * @return a new instance
     */
    static ModuleInfoRequires create() {
        return ModuleInfoRequires.builder().buildPrototype();
    }

    /**
     * Fluent API builder base for {@link ModuleInfoRequires}.
     *
     * @param <BUILDER>   type of the builder extending this abstract builder
     * @param <PROTOTYPE> type of the prototype interface that would be built by {@link #buildPrototype()}
     */
    abstract class BuilderBase<BUILDER extends ModuleInfoRequires.BuilderBase<BUILDER, PROTOTYPE>,
            PROTOTYPE extends ModuleInfoRequires>
            implements Prototype.Builder<BUILDER, PROTOTYPE> {

        private boolean isStatic;
        private boolean isTransitive;
        private String dependency;

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
        public BUILDER from(ModuleInfoRequires prototype) {
            isStatic(prototype.isStatic());
            isTransitive(prototype.isTransitive());
            dependency(prototype.dependency());
            return self();
        }

        /**
         * Update this builder from an existing prototype builder instance.
         *
         * @param builder existing builder prototype to update this builder from
         * @return updated builder instance
         */
        public BUILDER from(ModuleInfoRequires.BuilderBase<?, ?> builder) {
            isStatic(builder.isStatic());
            isTransitive(builder.isTransitive());
            builder.dependency().ifPresent(this::dependency);
            return self();
        }

        /**
         * Whether this is a {@code requires static} declaration.
         *
         * @param isStatic if requires static
         * @return updated builder instance
         * @see #isStatic()
         */
        public BUILDER isStatic(boolean isStatic) {
            this.isStatic = isStatic;
            return self();
        }

        /**
         * Whether this is a {@code requires transitive} declaration.
         *
         * @param isTransitive if requires transitive
         * @return updated builder instance
         * @see #isTransitive()
         */
        public BUILDER isTransitive(boolean isTransitive) {
            this.isTransitive = isTransitive;
            return self();
        }

        /**
         * The module we depend on.
         *
         * @param dependency module name we depend on
         * @return updated builder instance
         * @see #dependency()
         */
        public BUILDER dependency(String dependency) {
            Objects.requireNonNull(dependency);
            this.dependency = dependency;
            return self();
        }

        /**
         * Whether this is a {@code requires static} declaration.
         *
         * @return the is static
         */
        public boolean isStatic() {
            return isStatic;
        }

        /**
         * Whether this is a {@code requires transitive} declaration.
         *
         * @return the is transitive
         */
        public boolean isTransitive() {
            return isTransitive;
        }

        /**
         * The module we depend on.
         *
         * @return the dependency
         */
        public Optional<String> dependency() {
            return Optional.ofNullable(dependency);
        }

        @Override
        public String toString() {
            return "ModuleInfoRequiresBuilder{"
                    + "isStatic=" + isStatic + ","
                    + "isTransitive=" + isTransitive + ","
                    + "dependency=" + dependency
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
            Errors.Collector collector = Errors.collector();
            if (dependency == null) {
                collector.fatal(getClass(), "Property \"dependency\" must not be null, but not set");
            }
            collector.collect().checkValid();
        }

        /**
         * Generated implementation of the prototype, can be extended by descendant prototype implementations.
         */
        protected static class ModuleInfoRequiresImpl implements ModuleInfoRequires {

            private final boolean isStatic;
            private final boolean isTransitive;
            private final String dependency;

            /**
             * Create an instance providing a builder.
             *
             * @param builder extending builder base of this prototype
             */
            protected ModuleInfoRequiresImpl(ModuleInfoRequires.BuilderBase<?, ?> builder) {
                this.isStatic = builder.isStatic();
                this.isTransitive = builder.isTransitive();
                this.dependency = builder.dependency().get();
            }

            @Override
            public boolean isStatic() {
                return isStatic;
            }

            @Override
            public boolean isTransitive() {
                return isTransitive;
            }

            @Override
            public String dependency() {
                return dependency;
            }

            @Override
            public String toString() {
                return "ModuleInfoRequires{"
                        + "isStatic=" + isStatic + ","
                        + "isTransitive=" + isTransitive + ","
                        + "dependency=" + dependency
                        + "}";
            }

            @Override
            public boolean equals(Object o) {
                if (o == this) {
                    return true;
                }
                if (!(o instanceof ModuleInfoRequires other)) {
                    return false;
                }
                return isStatic == other.isStatic()
                        && isTransitive == other.isTransitive()
                        && Objects.equals(dependency, other.dependency());
            }

            @Override
            public int hashCode() {
                return Objects.hash(isStatic, isTransitive, dependency);
            }

        }

    }

    /**
     * Fluent API builder for {@link ModuleInfoRequires}.
     */
    class Builder extends ModuleInfoRequires.BuilderBase<ModuleInfoRequires.Builder, ModuleInfoRequires>
            implements io.helidon.common.Builder<ModuleInfoRequires.Builder, ModuleInfoRequires> {

        private Builder() {
        }

        @Override
        public ModuleInfoRequires buildPrototype() {
            preBuildPrototype();
            validatePrototype();
            return new ModuleInfoRequiresImpl(this);
        }

        @Override
        public ModuleInfoRequires build() {
            return buildPrototype();
        }

    }

}
