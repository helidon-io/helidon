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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import io.helidon.builder.api.Prototype;
import io.helidon.common.Errors;

/**
 * An exports directive of a module info.
 *
 * @see #builder()
 * @see #create()
 */
public interface ModuleInfoExports extends ModuleInfoExportsBlueprint, Prototype.Api {

    /**
     * Create a new fluent API builder to customize configuration.
     *
     * @return a new builder
     */
    static ModuleInfoExports.Builder builder() {
        return new ModuleInfoExports.Builder();
    }

    /**
     * Create a new fluent API builder from an existing instance.
     *
     * @param instance an existing instance used as a base for the builder
     * @return a builder based on an instance
     */
    static ModuleInfoExports.Builder builder(ModuleInfoExports instance) {
        return ModuleInfoExports.builder().from(instance);
    }

    /**
     * Create a new instance with default values.
     *
     * @return a new instance
     */
    static ModuleInfoExports create() {
        return ModuleInfoExports.builder().buildPrototype();
    }

    /**
     * Fluent API builder base for {@link ModuleInfoExports}.
     *
     * @param <BUILDER>   type of the builder extending this abstract builder
     * @param <PROTOTYPE> type of the prototype interface that would be built by {@link #buildPrototype()}
     */
    abstract class BuilderBase<BUILDER extends ModuleInfoExports.BuilderBase<BUILDER, PROTOTYPE>,
            PROTOTYPE extends ModuleInfoExports>
            implements Prototype.Builder<BUILDER, PROTOTYPE> {

        private final List<String> targets = new ArrayList<>();
        private boolean isTargetsMutated;
        private String packageName;

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
        public BUILDER from(ModuleInfoExports prototype) {
            packageName(prototype.packageName());
            if (!isTargetsMutated) {
                targets.clear();
            }
            addTargets(prototype.targets());
            return self();
        }

        /**
         * Update this builder from an existing prototype builder instance.
         *
         * @param builder existing builder prototype to update this builder from
         * @return updated builder instance
         */
        public BUILDER from(ModuleInfoExports.BuilderBase<?, ?> builder) {
            builder.packageName().ifPresent(this::packageName);
            if (isTargetsMutated) {
                if (builder.isTargetsMutated) {
                    addTargets(builder.targets);
                }
            } else {
                targets.clear();
                addTargets(builder.targets);
            }
            return self();
        }

        /**
         * Name of the exported package.
         *
         * @param packageName package name
         * @return updated builder instance
         * @see #packageName()
         */
        public BUILDER packageName(String packageName) {
            Objects.requireNonNull(packageName);
            this.packageName = packageName;
            return self();
        }

        /**
         * Names of target modules, empty if exported without qualification.
         *
         * @param targets list of target modules
         * @return updated builder instance
         * @see #targets()
         */
        public BUILDER targets(List<String> targets) {
            Objects.requireNonNull(targets);
            isTargetsMutated = true;
            this.targets.clear();
            this.targets.addAll(targets);
            return self();
        }

        /**
         * Names of target modules, empty if exported without qualification.
         *
         * @param targets list of target modules
         * @return updated builder instance
         * @see #targets()
         */
        public BUILDER addTargets(List<String> targets) {
            Objects.requireNonNull(targets);
            isTargetsMutated = true;
            this.targets.addAll(targets);
            return self();
        }

        /**
         * Names of target modules, empty if exported without qualification.
         *
         * @param target list of target modules
         * @return updated builder instance
         * @see #targets()
         */
        public BUILDER addTarget(String target) {
            Objects.requireNonNull(target);
            this.targets.add(target);
            isTargetsMutated = true;
            return self();
        }

        /**
         * Name of the exported package.
         *
         * @return the package name
         */
        public Optional<String> packageName() {
            return Optional.ofNullable(packageName);
        }

        /**
         * Names of target modules, empty if exported without qualification.
         *
         * @return the targets
         */
        public List<String> targets() {
            return targets;
        }

        @Override
        public String toString() {
            return "ModuleInfoExportsBuilder{"
                    + "packageName=" + packageName + ","
                    + "targets=" + targets
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
            if (packageName == null) {
                collector.fatal(getClass(), "Property \"packageName\" must not be null, but not set");
            }
            collector.collect().checkValid();
        }

        /**
         * Generated implementation of the prototype, can be extended by descendant prototype implementations.
         */
        protected static class ModuleInfoExportsImpl implements ModuleInfoExports {

            private final List<String> targets;
            private final String packageName;

            /**
             * Create an instance providing a builder.
             *
             * @param builder extending builder base of this prototype
             */
            protected ModuleInfoExportsImpl(ModuleInfoExports.BuilderBase<?, ?> builder) {
                this.packageName = builder.packageName().get();
                this.targets = List.copyOf(builder.targets());
            }

            @Override
            public String packageName() {
                return packageName;
            }

            @Override
            public List<String> targets() {
                return targets;
            }

            @Override
            public String toString() {
                return "ModuleInfoExports{"
                        + "packageName=" + packageName + ","
                        + "targets=" + targets
                        + "}";
            }

            @Override
            public boolean equals(Object o) {
                if (o == this) {
                    return true;
                }
                if (!(o instanceof ModuleInfoExports other)) {
                    return false;
                }
                return Objects.equals(packageName, other.packageName())
                        && Objects.equals(targets, other.targets());
            }

            @Override
            public int hashCode() {
                return Objects.hash(packageName, targets);
            }

        }

    }

    /**
     * Fluent API builder for {@link ModuleInfoExports}.
     */
    class Builder extends ModuleInfoExports.BuilderBase<ModuleInfoExports.Builder, ModuleInfoExports>
            implements io.helidon.common.Builder<ModuleInfoExports.Builder, ModuleInfoExports> {

        private Builder() {
        }

        @Override
        public ModuleInfoExports buildPrototype() {
            preBuildPrototype();
            validatePrototype();
            return new ModuleInfoExportsImpl(this);
        }

        @Override
        public ModuleInfoExports build() {
            return buildPrototype();
        }

    }

}
