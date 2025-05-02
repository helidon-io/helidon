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
import java.util.function.Consumer;

import io.helidon.builder.api.Prototype;
import io.helidon.common.Errors;

/**
 * Module info type information.
 *
 * @see #builder()
 * @see #create()
 */
public interface ModuleTypeInfo extends ModuleTypeInfoBlueprint, Prototype.Api {

    /**
     * Create a new fluent API builder to customize configuration.
     *
     * @return a new builder
     */
    static ModuleTypeInfo.Builder builder() {
        return new ModuleTypeInfo.Builder();
    }

    /**
     * Create a new fluent API builder from an existing instance.
     *
     * @param instance an existing instance used as a base for the builder
     * @return a builder based on an instance
     */
    static ModuleTypeInfo.Builder builder(ModuleTypeInfo instance) {
        return ModuleTypeInfo.builder().from(instance);
    }

    /**
     * Create a new instance with default values.
     *
     * @return a new instance
     */
    static ModuleTypeInfo create() {
        return ModuleTypeInfo.builder().buildPrototype();
    }

    /**
     * Fluent API builder base for {@link ModuleTypeInfo}.
     *
     * @param <BUILDER>   type of the builder extending this abstract builder
     * @param <PROTOTYPE> type of the prototype interface that would be built by {@link #buildPrototype()}
     */
    abstract class BuilderBase<BUILDER extends ModuleTypeInfo.BuilderBase<BUILDER, PROTOTYPE>, PROTOTYPE extends ModuleTypeInfo>
            implements Prototype.Builder<BUILDER, PROTOTYPE> {

        private final List<Annotation> annotations = new ArrayList<>();
        private final List<Annotation> inheritedAnnotations = new ArrayList<>();
        private final List<ModuleInfoExports> exports = new ArrayList<>();
        private final List<ModuleInfoOpens> opens = new ArrayList<>();
        private final List<ModuleInfoProvides> provides = new ArrayList<>();
        private final List<ModuleInfoRequires> requires = new ArrayList<>();
        private final List<ModuleInfoUses> uses = new ArrayList<>();
        private boolean isAnnotationsMutated;
        private boolean isExportsMutated;
        private boolean isInheritedAnnotationsMutated;
        private boolean isOpen;
        private boolean isOpensMutated;
        private boolean isProvidesMutated;
        private boolean isRequiresMutated;
        private boolean isUsesMutated;
        private Object originatingElement;
        private String description;
        private String name;

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
        public BUILDER from(ModuleTypeInfo prototype) {
            name(prototype.name());
            description(prototype.description());
            isOpen(prototype.isOpen());
            if (!isRequiresMutated) {
                requires.clear();
            }
            addRequires(prototype.requires());
            if (!isExportsMutated) {
                exports.clear();
            }
            addExports(prototype.exports());
            if (!isOpensMutated) {
                opens.clear();
            }
            addOpens(prototype.opens());
            if (!isUsesMutated) {
                uses.clear();
            }
            addUses(prototype.uses());
            if (!isProvidesMutated) {
                provides.clear();
            }
            addProvides(prototype.provides());
            originatingElement(prototype.originatingElement());
            if (!isAnnotationsMutated) {
                annotations.clear();
            }
            addAnnotations(prototype.annotations());
            if (!isInheritedAnnotationsMutated) {
                inheritedAnnotations.clear();
            }
            addInheritedAnnotations(prototype.inheritedAnnotations());
            return self();
        }

        /**
         * Update this builder from an existing prototype builder instance.
         *
         * @param builder existing builder prototype to update this builder from
         * @return updated builder instance
         */
        public BUILDER from(ModuleTypeInfo.BuilderBase<?, ?> builder) {
            builder.name().ifPresent(this::name);
            builder.description().ifPresent(this::description);
            isOpen(builder.isOpen());
            if (isRequiresMutated) {
                if (builder.isRequiresMutated) {
                    addRequires(builder.requires);
                }
            } else {
                requires.clear();
                addRequires(builder.requires);
            }
            if (isExportsMutated) {
                if (builder.isExportsMutated) {
                    addExports(builder.exports);
                }
            } else {
                exports.clear();
                addExports(builder.exports);
            }
            if (isOpensMutated) {
                if (builder.isOpensMutated) {
                    addOpens(builder.opens);
                }
            } else {
                opens.clear();
                addOpens(builder.opens);
            }
            if (isUsesMutated) {
                if (builder.isUsesMutated) {
                    addUses(builder.uses);
                }
            } else {
                uses.clear();
                addUses(builder.uses);
            }
            if (isProvidesMutated) {
                if (builder.isProvidesMutated) {
                    addProvides(builder.provides);
                }
            } else {
                provides.clear();
                addProvides(builder.provides);
            }
            builder.originatingElement().ifPresent(this::originatingElement);
            if (isAnnotationsMutated) {
                if (builder.isAnnotationsMutated) {
                    addAnnotations(builder.annotations);
                }
            } else {
                annotations.clear();
                addAnnotations(builder.annotations);
            }
            if (isInheritedAnnotationsMutated) {
                if (builder.isInheritedAnnotationsMutated) {
                    addInheritedAnnotations(builder.inheritedAnnotations);
                }
            } else {
                inheritedAnnotations.clear();
                addInheritedAnnotations(builder.inheritedAnnotations);
            }
            return self();
        }

        /**
         * Module name.
         *
         * @param name name of this module
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
         * @see #description()
         */
        public BUILDER clearDescription() {
            this.description = null;
            return self();
        }

        /**
         * Description, such as javadoc, if available.
         *
         * @param description description of this element
         * @return updated builder instance
         * @see #description()
         */
        public BUILDER description(String description) {
            Objects.requireNonNull(description);
            this.description = description;
            return self();
        }

        /**
         * Whether this is an open module.
         *
         * @param isOpen if open
         * @return updated builder instance
         * @see #isOpen()
         */
        public BUILDER isOpen(boolean isOpen) {
            this.isOpen = isOpen;
            return self();
        }

        /**
         * List of requires directives.
         *
         * @param requires requires
         * @return updated builder instance
         * @see #requires()
         */
        public BUILDER requires(List<? extends ModuleInfoRequires> requires) {
            Objects.requireNonNull(requires);
            isRequiresMutated = true;
            this.requires.clear();
            this.requires.addAll(requires);
            return self();
        }

        /**
         * List of requires directives.
         *
         * @param requires requires
         * @return updated builder instance
         * @see #requires()
         */
        public BUILDER addRequires(List<? extends ModuleInfoRequires> requires) {
            Objects.requireNonNull(requires);
            isRequiresMutated = true;
            this.requires.addAll(requires);
            return self();
        }

        /**
         * List of requires directives.
         *
         * @param require requires
         * @return updated builder instance
         * @see #requires()
         */
        public BUILDER addRequire(ModuleInfoRequires require) {
            Objects.requireNonNull(require);
            this.requires.add(require);
            isRequiresMutated = true;
            return self();
        }

        /**
         * List of requires directives.
         *
         * @param consumer requires
         * @return updated builder instance
         * @see #requires()
         */
        public BUILDER addRequire(Consumer<ModuleInfoRequires.Builder> consumer) {
            Objects.requireNonNull(consumer);
            var builder = ModuleInfoRequires.builder();
            consumer.accept(builder);
            this.requires.add(builder.build());
            return self();
        }

        /**
         * List of exports directives.
         *
         * @param exports exports
         * @return updated builder instance
         * @see #exports()
         */
        public BUILDER exports(List<? extends ModuleInfoExports> exports) {
            Objects.requireNonNull(exports);
            isExportsMutated = true;
            this.exports.clear();
            this.exports.addAll(exports);
            return self();
        }

        /**
         * List of exports directives.
         *
         * @param exports exports
         * @return updated builder instance
         * @see #exports()
         */
        public BUILDER addExports(List<? extends ModuleInfoExports> exports) {
            Objects.requireNonNull(exports);
            isExportsMutated = true;
            this.exports.addAll(exports);
            return self();
        }

        /**
         * List of exports directives.
         *
         * @param export exports
         * @return updated builder instance
         * @see #exports()
         */
        public BUILDER addExport(ModuleInfoExports export) {
            Objects.requireNonNull(export);
            this.exports.add(export);
            isExportsMutated = true;
            return self();
        }

        /**
         * List of exports directives.
         *
         * @param consumer exports
         * @return updated builder instance
         * @see #exports()
         */
        public BUILDER addExport(Consumer<ModuleInfoExports.Builder> consumer) {
            Objects.requireNonNull(consumer);
            var builder = ModuleInfoExports.builder();
            consumer.accept(builder);
            this.exports.add(builder.build());
            return self();
        }

        /**
         * List of opens directives.
         *
         * @param opens opens
         * @return updated builder instance
         * @see #opens()
         */
        public BUILDER opens(List<? extends ModuleInfoOpens> opens) {
            Objects.requireNonNull(opens);
            isOpensMutated = true;
            this.opens.clear();
            this.opens.addAll(opens);
            return self();
        }

        /**
         * List of opens directives.
         *
         * @param opens opens
         * @return updated builder instance
         * @see #opens()
         */
        public BUILDER addOpens(List<? extends ModuleInfoOpens> opens) {
            Objects.requireNonNull(opens);
            isOpensMutated = true;
            this.opens.addAll(opens);
            return self();
        }

        /**
         * List of opens directives.
         *
         * @param open opens
         * @return updated builder instance
         * @see #opens()
         */
        public BUILDER addOpen(ModuleInfoOpens open) {
            Objects.requireNonNull(open);
            this.opens.add(open);
            isOpensMutated = true;
            return self();
        }

        /**
         * List of opens directives.
         *
         * @param consumer opens
         * @return updated builder instance
         * @see #opens()
         */
        public BUILDER addOpen(Consumer<ModuleInfoOpens.Builder> consumer) {
            Objects.requireNonNull(consumer);
            var builder = ModuleInfoOpens.builder();
            consumer.accept(builder);
            this.opens.add(builder.build());
            return self();
        }

        /**
         * List of uses directives.
         *
         * @param uses uses
         * @return updated builder instance
         * @see #uses()
         */
        public BUILDER uses(List<? extends ModuleInfoUses> uses) {
            Objects.requireNonNull(uses);
            isUsesMutated = true;
            this.uses.clear();
            this.uses.addAll(uses);
            return self();
        }

        /**
         * List of uses directives.
         *
         * @param uses uses
         * @return updated builder instance
         * @see #uses()
         */
        public BUILDER addUses(List<? extends ModuleInfoUses> uses) {
            Objects.requireNonNull(uses);
            isUsesMutated = true;
            this.uses.addAll(uses);
            return self();
        }

        /**
         * List of uses directives.
         *
         * @param use uses
         * @return updated builder instance
         * @see #uses()
         */
        public BUILDER addUse(ModuleInfoUses use) {
            Objects.requireNonNull(use);
            this.uses.add(use);
            isUsesMutated = true;
            return self();
        }

        /**
         * List of uses directives.
         *
         * @param consumer uses
         * @return updated builder instance
         * @see #uses()
         */
        public BUILDER addUse(Consumer<ModuleInfoUses.Builder> consumer) {
            Objects.requireNonNull(consumer);
            var builder = ModuleInfoUses.builder();
            consumer.accept(builder);
            this.uses.add(builder.build());
            return self();
        }

        /**
         * List of provides directives.
         *
         * @param provides provides
         * @return updated builder instance
         * @see #provides()
         */
        public BUILDER provides(List<? extends ModuleInfoProvides> provides) {
            Objects.requireNonNull(provides);
            isProvidesMutated = true;
            this.provides.clear();
            this.provides.addAll(provides);
            return self();
        }

        /**
         * List of provides directives.
         *
         * @param provides provides
         * @return updated builder instance
         * @see #provides()
         */
        public BUILDER addProvides(List<? extends ModuleInfoProvides> provides) {
            Objects.requireNonNull(provides);
            isProvidesMutated = true;
            this.provides.addAll(provides);
            return self();
        }

        /**
         * List of provides directives.
         *
         * @param provide provides
         * @return updated builder instance
         * @see #provides()
         */
        public BUILDER addProvide(ModuleInfoProvides provide) {
            Objects.requireNonNull(provide);
            this.provides.add(provide);
            isProvidesMutated = true;
            return self();
        }

        /**
         * List of provides directives.
         *
         * @param consumer provides
         * @return updated builder instance
         * @see #provides()
         */
        public BUILDER addProvide(Consumer<ModuleInfoProvides.Builder> consumer) {
            Objects.requireNonNull(consumer);
            var builder = ModuleInfoProvides.builder();
            consumer.accept(builder);
            this.provides.add(builder.build());
            return self();
        }

        /**
         * Clear existing value of this property.
         *
         * @return updated builder instance
         * @see #originatingElement()
         */
        public BUILDER clearOriginatingElement() {
            this.originatingElement = null;
            return self();
        }

        /**
         * The element used to create this instance.
         * The type of the object depends on the environment we are in - it may be an {@code TypeElement} in annotation
         * processing,
         * or a {@code ClassInfo} when using classpath scanning.
         *
         * @param originatingElement originating element
         * @return updated builder instance
         * @see #originatingElement()
         */
        public BUILDER originatingElement(Object originatingElement) {
            Objects.requireNonNull(originatingElement);
            this.originatingElement = originatingElement;
            return self();
        }

        /**
         * List of declared and known annotations for this element.
         * Note that "known" implies that the annotation is visible, which depends
         * upon the context in which it was build (such as the {@link java.lang.annotation.Retention of the annotation}).
         *
         * @param annotations the list of annotations declared on this element
         * @return updated builder instance
         * @see #annotations()
         */
        public BUILDER annotations(List<? extends Annotation> annotations) {
            Objects.requireNonNull(annotations);
            isAnnotationsMutated = true;
            this.annotations.clear();
            this.annotations.addAll(annotations);
            return self();
        }

        /**
         * List of declared and known annotations for this element.
         * Note that "known" implies that the annotation is visible, which depends
         * upon the context in which it was build (such as the {@link java.lang.annotation.Retention of the annotation}).
         *
         * @param annotations the list of annotations declared on this element
         * @return updated builder instance
         * @see #annotations()
         */
        public BUILDER addAnnotations(List<? extends Annotation> annotations) {
            Objects.requireNonNull(annotations);
            isAnnotationsMutated = true;
            this.annotations.addAll(annotations);
            return self();
        }

        /**
         * List of declared and known annotations for this element.
         * Note that "known" implies that the annotation is visible, which depends
         * upon the context in which it was build (such as the {@link java.lang.annotation.Retention of the annotation}).
         *
         * @param annotation the list of annotations declared on this element
         * @return updated builder instance
         * @see #annotations()
         */
        public BUILDER addAnnotation(Annotation annotation) {
            Objects.requireNonNull(annotation);
            this.annotations.add(annotation);
            isAnnotationsMutated = true;
            return self();
        }

        /**
         * List of declared and known annotations for this element.
         * Note that "known" implies that the annotation is visible, which depends
         * upon the context in which it was build (such as the {@link java.lang.annotation.Retention of the annotation}).
         *
         * @param consumer the list of annotations declared on this element
         * @return updated builder instance
         * @see #annotations()
         */
        public BUILDER addAnnotation(Consumer<Annotation.Builder> consumer) {
            Objects.requireNonNull(consumer);
            var builder = Annotation.builder();
            consumer.accept(builder);
            this.annotations.add(builder.build());
            return self();
        }

        /**
         * List of all inherited annotations for this element. Inherited annotations are annotations declared
         * on annotations of this element that are also marked as {@link java.lang.annotation.Inherited}.
         * <p>
         * The returned list does not contain {@link #annotations()}. If a meta-annotation is present on multiple
         * annotations, it will be returned once for each such declaration.
         * <p>
         * This method does not return annotations on super types or interfaces!
         *
         * @param inheritedAnnotations list of all meta annotations of this element
         * @return updated builder instance
         * @see #inheritedAnnotations()
         */
        public BUILDER inheritedAnnotations(List<? extends Annotation> inheritedAnnotations) {
            Objects.requireNonNull(inheritedAnnotations);
            isInheritedAnnotationsMutated = true;
            this.inheritedAnnotations.clear();
            this.inheritedAnnotations.addAll(inheritedAnnotations);
            return self();
        }

        /**
         * List of all inherited annotations for this element. Inherited annotations are annotations declared
         * on annotations of this element that are also marked as {@link java.lang.annotation.Inherited}.
         * <p>
         * The returned list does not contain {@link #annotations()}. If a meta-annotation is present on multiple
         * annotations, it will be returned once for each such declaration.
         * <p>
         * This method does not return annotations on super types or interfaces!
         *
         * @param inheritedAnnotations list of all meta annotations of this element
         * @return updated builder instance
         * @see #inheritedAnnotations()
         */
        public BUILDER addInheritedAnnotations(List<? extends Annotation> inheritedAnnotations) {
            Objects.requireNonNull(inheritedAnnotations);
            isInheritedAnnotationsMutated = true;
            this.inheritedAnnotations.addAll(inheritedAnnotations);
            return self();
        }

        /**
         * List of all inherited annotations for this element. Inherited annotations are annotations declared
         * on annotations of this element that are also marked as {@link java.lang.annotation.Inherited}.
         * <p>
         * The returned list does not contain {@link #annotations()}. If a meta-annotation is present on multiple
         * annotations, it will be returned once for each such declaration.
         * <p>
         * This method does not return annotations on super types or interfaces!
         *
         * @param inheritedAnnotation list of all meta annotations of this element
         * @return updated builder instance
         * @see #inheritedAnnotations()
         */
        public BUILDER addInheritedAnnotation(Annotation inheritedAnnotation) {
            Objects.requireNonNull(inheritedAnnotation);
            this.inheritedAnnotations.add(inheritedAnnotation);
            isInheritedAnnotationsMutated = true;
            return self();
        }

        /**
         * List of all inherited annotations for this element. Inherited annotations are annotations declared
         * on annotations of this element that are also marked as {@link java.lang.annotation.Inherited}.
         * <p>
         * The returned list does not contain {@link #annotations()}. If a meta-annotation is present on multiple
         * annotations, it will be returned once for each such declaration.
         * <p>
         * This method does not return annotations on super types or interfaces!
         *
         * @param consumer list of all meta annotations of this element
         * @return updated builder instance
         * @see #inheritedAnnotations()
         */
        public BUILDER addInheritedAnnotation(Consumer<Annotation.Builder> consumer) {
            Objects.requireNonNull(consumer);
            var builder = Annotation.builder();
            consumer.accept(builder);
            this.inheritedAnnotations.add(builder.build());
            return self();
        }

        /**
         * Module name.
         *
         * @return the name
         */
        public Optional<String> name() {
            return Optional.ofNullable(name);
        }

        /**
         * Description, such as javadoc, if available.
         *
         * @return the description
         */
        public Optional<String> description() {
            return Optional.ofNullable(description);
        }

        /**
         * Whether this is an open module.
         *
         * @return the is open
         */
        public boolean isOpen() {
            return isOpen;
        }

        /**
         * List of requires directives.
         *
         * @return the requires
         */
        public List<ModuleInfoRequires> requires() {
            return requires;
        }

        /**
         * List of exports directives.
         *
         * @return the exports
         */
        public List<ModuleInfoExports> exports() {
            return exports;
        }

        /**
         * List of opens directives.
         *
         * @return the opens
         */
        public List<ModuleInfoOpens> opens() {
            return opens;
        }

        /**
         * List of uses directives.
         *
         * @return the uses
         */
        public List<ModuleInfoUses> uses() {
            return uses;
        }

        /**
         * List of provides directives.
         *
         * @return the provides
         */
        public List<ModuleInfoProvides> provides() {
            return provides;
        }

        /**
         * The element used to create this instance.
         * The type of the object depends on the environment we are in - it may be an {@code TypeElement} in annotation
         * processing,
         * or a {@code ClassInfo} when using classpath scanning.
         *
         * @return the originating element
         */
        public Optional<Object> originatingElement() {
            return Optional.ofNullable(originatingElement);
        }

        /**
         * List of declared and known annotations for this element.
         * Note that "known" implies that the annotation is visible, which depends
         * upon the context in which it was build (such as the {@link java.lang.annotation.Retention of the annotation}).
         *
         * @return the annotations
         */
        public List<Annotation> annotations() {
            return annotations;
        }

        /**
         * List of all inherited annotations for this element. Inherited annotations are annotations declared
         * on annotations of this element that are also marked as {@link java.lang.annotation.Inherited}.
         * <p>
         * The returned list does not contain {@link #annotations()}. If a meta-annotation is present on multiple
         * annotations, it will be returned once for each such declaration.
         * <p>
         * This method does not return annotations on super types or interfaces!
         *
         * @return the inherited annotations
         */
        public List<Annotation> inheritedAnnotations() {
            return inheritedAnnotations;
        }

        @Override
        public String toString() {
            return "ModuleTypeInfoBuilder{"
                    + "name=" + name + ","
                    + "isOpen=" + isOpen + ","
                    + "requires=" + requires + ","
                    + "exports=" + exports + ","
                    + "opens=" + opens + ","
                    + "uses=" + uses + ","
                    + "provides=" + provides + ","
                    + "annotations=" + annotations + ","
                    + "inheritedAnnotations=" + inheritedAnnotations
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
         * Description, such as javadoc, if available.
         *
         * @param description description of this element
         * @return updated builder instance
         * @see #description()
         */
        BUILDER description(Optional<String> description) {
            Objects.requireNonNull(description);
            this.description = description.map(s -> s).orElse(this.description);
            return self();
        }

        /**
         * The element used to create this instance.
         * The type of the object depends on the environment we are in - it may be an {@code TypeElement} in annotation
         * processing,
         * or a {@code ClassInfo} when using classpath scanning.
         *
         * @param originatingElement originating element
         * @return updated builder instance
         * @see #originatingElement()
         */
        BUILDER originatingElement(Optional<?> originatingElement) {
            Objects.requireNonNull(originatingElement);
            this.originatingElement = originatingElement.map(java.lang.Object.class::cast).orElse(this.originatingElement);
            return self();
        }

        /**
         * Generated implementation of the prototype, can be extended by descendant prototype implementations.
         */
        protected static class ModuleTypeInfoImpl implements ModuleTypeInfo {

            private final boolean isOpen;
            private final List<Annotation> annotations;
            private final List<Annotation> inheritedAnnotations;
            private final List<ModuleInfoExports> exports;
            private final List<ModuleInfoOpens> opens;
            private final List<ModuleInfoProvides> provides;
            private final List<ModuleInfoRequires> requires;
            private final List<ModuleInfoUses> uses;
            private final Optional<Object> originatingElement;
            private final Optional<String> description;
            private final String name;

            /**
             * Create an instance providing a builder.
             *
             * @param builder extending builder base of this prototype
             */
            protected ModuleTypeInfoImpl(ModuleTypeInfo.BuilderBase<?, ?> builder) {
                this.name = builder.name().get();
                this.description = builder.description();
                this.isOpen = builder.isOpen();
                this.requires = List.copyOf(builder.requires());
                this.exports = List.copyOf(builder.exports());
                this.opens = List.copyOf(builder.opens());
                this.uses = List.copyOf(builder.uses());
                this.provides = List.copyOf(builder.provides());
                this.originatingElement = builder.originatingElement();
                this.annotations = List.copyOf(builder.annotations());
                this.inheritedAnnotations = List.copyOf(builder.inheritedAnnotations());
            }

            @Override
            public String name() {
                return name;
            }

            @Override
            public Optional<String> description() {
                return description;
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
            public List<ModuleInfoExports> exports() {
                return exports;
            }

            @Override
            public List<ModuleInfoOpens> opens() {
                return opens;
            }

            @Override
            public List<ModuleInfoUses> uses() {
                return uses;
            }

            @Override
            public List<ModuleInfoProvides> provides() {
                return provides;
            }

            @Override
            public Optional<Object> originatingElement() {
                return originatingElement;
            }

            @Override
            public List<Annotation> annotations() {
                return annotations;
            }

            @Override
            public List<Annotation> inheritedAnnotations() {
                return inheritedAnnotations;
            }

            @Override
            public String toString() {
                return "ModuleTypeInfo{"
                        + "name=" + name + ","
                        + "isOpen=" + isOpen + ","
                        + "requires=" + requires + ","
                        + "exports=" + exports + ","
                        + "opens=" + opens + ","
                        + "uses=" + uses + ","
                        + "provides=" + provides + ","
                        + "annotations=" + annotations + ","
                        + "inheritedAnnotations=" + inheritedAnnotations
                        + "}";
            }

            @Override
            public boolean equals(Object o) {
                if (o == this) {
                    return true;
                }
                if (!(o instanceof ModuleTypeInfo other)) {
                    return false;
                }
                return Objects.equals(name, other.name())
                        && isOpen == other.isOpen()
                        && Objects.equals(requires, other.requires())
                        && Objects.equals(exports, other.exports())
                        && Objects.equals(opens, other.opens())
                        && Objects.equals(uses, other.uses())
                        && Objects.equals(provides, other.provides())
                        && Objects.equals(annotations, other.annotations())
                        && Objects.equals(inheritedAnnotations, other.inheritedAnnotations());
            }

            @Override
            public int hashCode() {
                return Objects.hash(name, isOpen, requires, exports, opens, uses, provides, annotations, inheritedAnnotations);
            }

        }

    }

    /**
     * Fluent API builder for {@link ModuleTypeInfo}.
     */
    class Builder extends ModuleTypeInfo.BuilderBase<ModuleTypeInfo.Builder, ModuleTypeInfo>
            implements io.helidon.common.Builder<ModuleTypeInfo.Builder, ModuleTypeInfo> {

        private Builder() {
        }

        @Override
        public ModuleTypeInfo buildPrototype() {
            preBuildPrototype();
            validatePrototype();
            return new ModuleTypeInfoImpl(this);
        }

        @Override
        public ModuleTypeInfo build() {
            return buildPrototype();
        }

    }

}
