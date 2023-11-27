/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.codegen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import io.helidon.common.Errors;
import io.helidon.common.types.TypeName;

/**
 * Module info.
 *
 * @see #builder()
 */
public interface ModuleInfo {
    /**
     * The default module name (i.e., "unnamed").
     */
    String DEFAULT_MODULE_NAME = "unnamed";
    /**
     * The file name.
     */
    String FILE_NAME = "module-info.java";

    /**
     * Create a new fluent API builder to customize configuration.
     *
     * @return a new builder
     */
    static Builder builder() {
        return new Builder();
    }

    /**
     * Create a new fluent API builder from an existing instance.
     *
     * @param instance an existing instance used as a base for the builder
     * @return a builder based on an instance
     */
    static Builder builder(ModuleInfo instance) {
        return ModuleInfo.builder().from(instance);
    }

    /**
     * Name of the module.
     *
     * @return module name
     */
    String name();

    /**
     * Whether this module is declared as open module.
     *
     * @return whether this module is open
     */
    boolean isOpen();

    /**
     * Declared dependencies of the module.
     *
     * @return list of requires
     */
    List<ModuleInfoRequires> requires();

    /**
     * Exports of the module.
     *
     * @return map of exported packages (exports x.y.z to a.b.cSomeModule).
     */
    Map<String, List<String>> exports();

    /**
     * Used service loader providers.
     *
     * @return list of used provider interfaces
     */
    List<TypeName> uses();

    /**
     * Map of provider interfaces to provider implementations provided by this module.
     *
     * @return map of interface to implementations
     */
    Map<TypeName, List<TypeName>> provides();

    /**
     * Map of opened packages to modules (if any).
     *
     * @return map of package to modules
     */
    Map<String, List<String>> opens();

    /**
     * first export that does not export to a specific module (if present).
     *
     * @return package that is exported
     */
    default Optional<String> firstUnqualifiedExport() {
        return exports().entrySet()
                .stream()
                .filter(it -> it.getValue().isEmpty())
                .map(Map.Entry::getKey)
                .findFirst();
    }

    /**
     * Fluent API builder base for {@link io.helidon.codegen.ModuleInfo}.
     *
     * @param <BUILDER> type of the builder extending this abstract builder
     */
    abstract class BuilderBase<BUILDER extends BuilderBase<BUILDER>> implements io.helidon.common.Builder<BUILDER, ModuleInfo> {

        private final List<TypeName> uses = new ArrayList<>();
        private final List<ModuleInfoRequires> requires = new ArrayList<>();
        private final Map<String, List<String>> exports = new LinkedHashMap<>();
        private final Map<TypeName, List<TypeName>> provides = new LinkedHashMap<>();
        private final Map<String, List<String>> opens = new LinkedHashMap<>();
        private boolean isOpen = false;
        private String name;

        /**
         * Protected to support extensibility.
         */
        protected BuilderBase() {
        }

        /**
         * Update this builder from an existing prototype instance.
         *
         * @param prototype existing prototype to update this builder from
         * @return updated builder instance
         */
        public BUILDER from(ModuleInfo prototype) {
            name(prototype.name());
            isOpen(prototype.isOpen());
            addRequires(prototype.requires());
            addExports(prototype.exports());
            addUses(prototype.uses());
            addProvides(prototype.provides());
            addOpens(prototype.opens());
            return identity();
        }

        /**
         * Update this builder from an existing prototype builder instance.
         *
         * @param builder existing builder prototype to update this builder from
         * @return updated builder instance
         */
        public BUILDER from(BuilderBase<?> builder) {
            builder.name().ifPresent(this::name);
            isOpen(builder.isOpen());
            addRequires(builder.requires());
            addExports(builder.exports());
            addUses(builder.uses());
            addProvides(builder.provides());
            addOpens(builder.opens());
            return identity();
        }

        /**
         * Name of the module.
         *
         * @param name module name
         * @return updated builder instance
         * @see #name()
         */
        public BUILDER name(String name) {
            Objects.requireNonNull(name);
            this.name = name;
            return identity();
        }

        /**
         * Whether this module is declared as open module.
         *
         * @param isOpen whether this module is open
         * @return updated builder instance
         * @see #isOpen()
         */
        public BUILDER isOpen(boolean isOpen) {
            this.isOpen = isOpen;
            return identity();
        }

        /**
         * Declared dependencies of the module.
         *
         * @param requires list of requires
         * @return updated builder instance
         * @see #requires()
         */
        public BUILDER requires(List<? extends ModuleInfoRequires> requires) {
            Objects.requireNonNull(requires);
            this.requires.clear();
            this.requires.addAll(requires);
            return identity();
        }

        /**
         * Declared dependencies of the module.
         *
         * @param requires list of requires
         * @return updated builder instance
         * @see #requires()
         */
        public BUILDER addRequires(List<? extends ModuleInfoRequires> requires) {
            Objects.requireNonNull(requires);
            this.requires.addAll(requires);
            return identity();
        }

        /**
         * Declared dependencies of the module.
         *
         * @param require list of requires
         * @return updated builder instance
         * @see #requires()
         */
        public BUILDER addRequire(ModuleInfoRequires require) {
            Objects.requireNonNull(require);
            this.requires.add(require);
            return identity();
        }

        /**
         * Exports of the module.
         *
         * @param exports list of exported packages
         * @return updated builder instance
         * @see #exports()
         */
        public BUILDER exports(Map<? extends String, List<String>> exports) {
            Objects.requireNonNull(exports);
            this.exports.clear();
            this.exports.putAll(exports);
            return identity();
        }

        /**
         * Exports of the module.
         *
         * @param exports list of exported packages
         * @return updated builder instance
         * @see #exports()
         */
        public BUILDER addExports(Map<? extends String, List<String>> exports) {
            Objects.requireNonNull(exports);
            this.exports.putAll(exports);
            return identity();
        }

        /**
         * This method adds a new value to the map, or replaces it if the key already exists.
         *
         * @param packageName  key to add or replace
         * @param moduleNames new value for the key
         * @return updated builder instance
         * @see #opens()
         */
        public BUILDER putExports(String packageName, List<String> moduleNames) {
            Objects.requireNonNull(packageName);
            Objects.requireNonNull(moduleNames);
            this.exports.put(packageName, List.copyOf(moduleNames));
            return identity();
        }

        /**
         * Exports of the module.
         *
         * @param export package to export
         * @param to exported to a module
         * @return updated builder instance
         * @see #exports()
         */
        public BUILDER addExport(String export, String to) {
            Objects.requireNonNull(export);
            Objects.requireNonNull(to);
            this.opens.compute(export, (k, v) -> {
                v = v == null ? new ArrayList<>() : new ArrayList<>(v);
                v.add(to);
                return v;
            });
            return identity();
        }

        /**
         * Used service loader providers.
         *
         * @param uses list of used provider interfaces
         * @return updated builder instance
         * @see #uses()
         */
        public BUILDER uses(List<? extends TypeName> uses) {
            Objects.requireNonNull(uses);
            this.uses.clear();
            this.uses.addAll(uses);
            return identity();
        }

        /**
         * Used service loader providers.
         *
         * @param uses list of used provider interfaces
         * @return updated builder instance
         * @see #uses()
         */
        public BUILDER addUses(List<? extends TypeName> uses) {
            Objects.requireNonNull(uses);
            this.uses.addAll(uses);
            return identity();
        }

        /**
         * Used service loader providers.
         *
         * @param use list of used provider interfaces
         * @return updated builder instance
         * @see #uses()
         */
        public BUILDER addUse(TypeName use) {
            Objects.requireNonNull(use);
            this.uses.add(use);
            return identity();
        }

        /**
         * Used service loader providers.
         *
         * @param consumer list of used provider interfaces
         * @return updated builder instance
         * @see #uses()
         */
        public BUILDER addUse(Consumer<TypeName.Builder> consumer) {
            Objects.requireNonNull(consumer);
            var builder = TypeName.builder();
            consumer.accept(builder);
            this.uses.add(builder.build());
            return identity();
        }

        /**
         * This method replaces all values with the new ones.
         *
         * @param provides map of interface to implementations
         * @return updated builder instance
         * @see #provides()
         */
        public BUILDER provides(Map<? extends TypeName, List<TypeName>> provides) {
            Objects.requireNonNull(provides);
            this.provides.clear();
            this.provides.putAll(provides);
            return identity();
        }

        /**
         * This method keeps existing values, then puts all new values into the map.
         *
         * @param provides map of interface to implementations
         * @return updated builder instance
         * @see #provides()
         */
        public BUILDER addProvides(Map<? extends TypeName, List<TypeName>> provides) {
            Objects.requireNonNull(provides);
            this.provides.putAll(provides);
            return identity();
        }

        /**
         * This method adds a new value to the map value, or creates a new value.
         *
         * @param key     key to add to
         * @param provide additional value for the key
         * @return updated builder instance
         * @see #provides()
         */
        public BUILDER addProvide(TypeName key, TypeName provide) {
            Objects.requireNonNull(key);
            Objects.requireNonNull(provide);
            this.provides.compute(key, (k, v) -> {
                v = v == null ? new ArrayList<>() : new ArrayList<>(v);
                v.add(provide);
                return v;
            });
            return identity();
        }

        /**
         * This method adds a new value to the map value, or creates a new value.
         *
         * @param key      key to add to
         * @param provides additional values for the key
         * @return updated builder instance
         * @see #provides()
         */
        public BUILDER addProvides(TypeName key, List<TypeName> provides) {
            Objects.requireNonNull(key);
            Objects.requireNonNull(provides);
            this.provides.compute(key, (k, v) -> {
                v = v == null ? new ArrayList<>() : new ArrayList<>(v);
                v.addAll(provides);
                return v;
            });
            return identity();
        }

        /**
         * This method adds a new value to the map, or replaces it if the key already exists.
         *
         * @param key     key to add or replace
         * @param provide new value for the key
         * @return updated builder instance
         * @see #provides()
         */
        public BUILDER putProvide(TypeName key, List<TypeName> provide) {
            Objects.requireNonNull(key);
            Objects.requireNonNull(provide);
            this.provides.put(key, List.copyOf(provide));
            return identity();
        }

        /**
         * This method replaces all values with the new ones.
         *
         * @param opens map of package to modules
         * @return updated builder instance
         * @see #opens()
         */
        public BUILDER opens(Map<? extends String, List<String>> opens) {
            Objects.requireNonNull(opens);
            this.opens.clear();
            this.opens.putAll(opens);
            return identity();
        }

        /**
         * This method keeps existing values, then puts all new values into the map.
         *
         * @param opens map of package to modules
         * @return updated builder instance
         * @see #opens()
         */
        public BUILDER addOpens(Map<? extends String, List<String>> opens) {
            Objects.requireNonNull(opens);
            this.opens.putAll(opens);
            return identity();
        }

        /**
         * This method adds a new value to the map value, or creates a new value.
         *
         * @param key  key to add to
         * @param open additional value for the key
         * @return updated builder instance
         * @see #opens()
         */
        public BUILDER addOpen(String key, String open) {
            Objects.requireNonNull(key);
            Objects.requireNonNull(open);
            this.opens.compute(key, (k, v) -> {
                v = v == null ? new ArrayList<>() : new ArrayList<>(v);
                v.add(open);
                return v;
            });
            return identity();
        }

        /**
         * This method adds a new value to the map value, or creates a new value.
         *
         * @param key   key to add to
         * @param opens additional values for the key
         * @return updated builder instance
         * @see #opens()
         */
        public BUILDER addOpens(String key, List<String> opens) {
            Objects.requireNonNull(key);
            Objects.requireNonNull(opens);
            this.opens.compute(key, (k, v) -> {
                v = v == null ? new ArrayList<>() : new ArrayList<>(v);
                v.addAll(opens);
                return v;
            });
            return identity();
        }

        /**
         * This method adds a new value to the map, or replaces it if the key already exists.
         *
         * @param key  key to add or replace
         * @param open new value for the key
         * @return updated builder instance
         * @see #opens()
         */
        public BUILDER putOpen(String key, List<String> open) {
            Objects.requireNonNull(key);
            Objects.requireNonNull(open);
            this.opens.put(key, List.copyOf(open));
            return identity();
        }

        /**
         * Name of the module.
         *
         * @return the name
         */
        public Optional<String> name() {
            return Optional.ofNullable(name);
        }

        /**
         * Whether this module is declared as open module.
         *
         * @return the is open
         */
        public boolean isOpen() {
            return isOpen;
        }

        /**
         * Declared dependencies of the module.
         *
         * @return the requires
         */
        public List<ModuleInfoRequires> requires() {
            return requires;
        }

        /**
         * Exports of the module.
         *
         * @return the exports
         */
        public Map<String, List<String>> exports() {
            return exports;
        }

        /**
         * Used service loader providers.
         *
         * @return the uses
         */
        public List<TypeName> uses() {
            return uses;
        }

        /**
         * Map of provider interfaces to provider implementations provided by this module.
         *
         * @return the provides
         */
        public Map<TypeName, List<TypeName>> provides() {
            return provides;
        }

        /**
         * Map of opened packages to modules (if any).
         *
         * @return the opens
         */
        public Map<String, List<String>> opens() {
            return opens;
        }

        @Override
        public String toString() {
            return "ModuleInfoBuilder{"
                    + "name=" + name + ","
                    + "isOpen=" + isOpen + ","
                    + "requires=" + requires + ","
                    + "exports=" + exports + ","
                    + "uses=" + uses + ","
                    + "provides=" + provides + ","
                    + "opens=" + opens
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
            if (name == null) {
                collector.fatal(getClass(), "Property \"name\" must not be null, but not set");
            }
            collector.collect().checkValid();
        }

        /**
         * Generated implementation of the prototype, can be extended by descendant prototype implementations.
         */
        protected static class ModuleInfoImpl implements ModuleInfo {

            private final boolean isOpen;
            private final List<TypeName> uses;
            private final List<ModuleInfoRequires> requires;
            private final Map<String, List<String>> exports;
            private final Map<TypeName, List<TypeName>> provides;
            private final Map<String, List<String>> opens;
            private final String name;

            /**
             * Create an instance providing a builder.
             *
             * @param builder extending builder base of this prototype
             */
            protected ModuleInfoImpl(ModuleInfo.BuilderBase<?> builder) {
                this.name = builder.name().get();
                this.isOpen = builder.isOpen();
                this.requires = List.copyOf(builder.requires());
                this.exports = Map.copyOf(builder.exports());
                this.uses = List.copyOf(builder.uses());
                this.provides = Collections.unmodifiableMap(new LinkedHashMap<>(builder.provides()));
                this.opens = Collections.unmodifiableMap(new LinkedHashMap<>(builder.opens()));
            }

            @Override
            public String name() {
                return name;
            }

            @Override
            public boolean isOpen() {
                return isOpen;
            }

            @Override
            public List<ModuleInfoRequires> requires() {
                return requires;
            }

            @Override
            public Map<String, List<String>> exports() {
                return exports;
            }

            @Override
            public List<TypeName> uses() {
                return uses;
            }

            @Override
            public Map<TypeName, List<TypeName>> provides() {
                return provides;
            }

            @Override
            public Map<String, List<String>> opens() {
                return opens;
            }

            @Override
            public String toString() {
                return "ModuleInfo{"
                        + "name=" + name + ","
                        + "isOpen=" + isOpen + ","
                        + "requires=" + requires + ","
                        + "exports=" + exports + ","
                        + "uses=" + uses + ","
                        + "provides=" + provides + ","
                        + "opens=" + opens
                        + "}";
            }

            @Override
            public boolean equals(Object o) {
                if (o == this) {
                    return true;
                }
                if (!(o instanceof ModuleInfo other)) {
                    return false;
                }
                return Objects.equals(name, other.name())
                        && isOpen == other.isOpen()
                        && Objects.equals(requires, other.requires())
                        && Objects.equals(exports, other.exports())
                        && Objects.equals(uses, other.uses())
                        && Objects.equals(provides, other.provides())
                        && Objects.equals(opens, other.opens());
            }

            @Override
            public int hashCode() {
                return Objects.hash(name, isOpen, requires, exports, uses, provides, opens);
            }

        }

    }

    /**
     * Fluent API builder for {@link io.helidon.codegen.ModuleInfo}.
     */
    class Builder extends BuilderBase<Builder> implements io.helidon.common.Builder<Builder, ModuleInfo> {

        private Builder() {
        }

        @Override
        public ModuleInfo build() {
            preBuildPrototype();
            validatePrototype();
            return new ModuleInfoImpl(this);
        }

    }

}
