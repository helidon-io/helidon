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
import java.util.function.Function;

import io.helidon.builder.api.Prototype;
import io.helidon.common.Errors;

/**
 * Module info type information.
 *
 * @see #builder()
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
     * The element used to create this instance, or {@link io.helidon.common.types.TypeInfo#typeName()} if none provided.
     * The type of the object depends on the environment we are in - it may be an {@code TypeElement} in annotation processing,
     * or a {@code ClassInfo} when using classpath scanning.
     *
     * @return originating element, or the type of this type info
     */
    default Object originatingElementValue() {
        return ModuleTypeInfoBlueprint.super.originatingElementValue();
    }

    /**
     * Module name.
     *
     * @return name of this module
     */
    @Override
    String name();

    /**
     * Description, such as javadoc, if available.
     *
     * @return description of this element
     */
    @Override
    Optional<String> description();

    /**
     * Whether this is an open module.
     *
     * @return if open
     */
    @Override
    boolean isOpen();

    /**
     * List of requires directives.
     *
     * @return requires
     */
    @Override
    List<ModuleInfoRequires> requires();

    /**
     * List of exports directives.
     *
     * @return exports
     */
    @Override
    List<ModuleInfoExports> exports();

    /**
     * List of opens directives.
     *
     * @return opens
     */
    @Override
    List<ModuleInfoOpens> opens();

    /**
     * List of uses directives.
     *
     * @return uses
     */
    @Override
    List<ModuleInfoUses> uses();

    /**
     * List of provides directives.
     *
     * @return provides
     */
    @Override
    List<ModuleInfoProvides> provides();

    /**
     * The element used to create this instance.
     * The type of the object depends on the environment we are in - it may be an {@code TypeElement} in annotation processing,
     * or a {@code ClassInfo} when using classpath scanning.
     *
     * @return originating element
     */
    @Override
    Optional<Object> originatingElement();

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
            if (!this.isRequiresMutated) {
                this.requires.clear();
            }
            addRequires(prototype.requires());
            if (!this.isExportsMutated) {
                this.exports.clear();
            }
            addExports(prototype.exports());
            if (!this.isOpensMutated) {
                this.opens.clear();
            }
            addOpens(prototype.opens());
            if (!this.isUsesMutated) {
                this.uses.clear();
            }
            addUses(prototype.uses());
            if (!this.isProvidesMutated) {
                this.provides.clear();
            }
            addProvides(prototype.provides());
            originatingElement(prototype.originatingElement());
            if (!this.isAnnotationsMutated) {
                this.annotations.clear();
            }
            addAnnotations(prototype.annotations());
            if (!this.isInheritedAnnotationsMutated) {
                this.inheritedAnnotations.clear();
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
            if (this.isRequiresMutated) {
                if (builder.isRequiresMutated) {
                    addRequires(builder.requires());
                }
            } else {
                requires(builder.requires());
            }
            if (this.isExportsMutated) {
                if (builder.isExportsMutated) {
                    addExports(builder.exports());
                }
            } else {
                exports(builder.exports());
            }
            if (this.isOpensMutated) {
                if (builder.isOpensMutated) {
                    addOpens(builder.opens());
                }
            } else {
                opens(builder.opens());
            }
            if (this.isUsesMutated) {
                if (builder.isUsesMutated) {
                    addUses(builder.uses());
                }
            } else {
                uses(builder.uses());
            }
            if (this.isProvidesMutated) {
                if (builder.isProvidesMutated) {
                    addProvides(builder.provides());
                }
            } else {
                provides(builder.provides());
            }
            builder.originatingElement().ifPresent(this::originatingElement);
            if (this.isAnnotationsMutated) {
                if (builder.isAnnotationsMutated) {
                    addAnnotations(builder.annotations());
                }
            } else {
                annotations(builder.annotations());
            }
            if (this.isInheritedAnnotationsMutated) {
                if (builder.isInheritedAnnotationsMutated) {
                    addInheritedAnnotations(builder.inheritedAnnotations());
                }
            } else {
                inheritedAnnotations(builder.inheritedAnnotations());
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
         * Clear existing value of description.
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
         * Clear all requires.
         *
         * @return updated builder instance
         * @see #requires()
         */
        public BUILDER clearRequires() {
            this.isRequiresMutated = true;
            this.requires.clear();
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
            this.isRequiresMutated = true;
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
            this.isRequiresMutated = true;
            this.requires.addAll(requires);
            return self();
        }

        /**
         * List of requires directives.
         *
         * @param require add single requires
         * @return updated builder instance
         * @see #requires()
         */
        public BUILDER addRequire(ModuleInfoRequires require) {
            Objects.requireNonNull(require);
            this.requires.add(require);
            this.isRequiresMutated = true;
            return self();
        }

        /**
         * List of requires directives.
         *
         * @param consumer consumer of builder for requires
         * @return updated builder instance
         * @see #requires()
         */
        public BUILDER addRequire(Consumer<ModuleInfoRequires.Builder> consumer) {
            Objects.requireNonNull(consumer);
            var builder = ModuleInfoRequires.builder();
            consumer.accept(builder);
            this.addRequire(builder.build());
            return self();
        }

        /**
         * Clear all exports.
         *
         * @return updated builder instance
         * @see #exports()
         */
        public BUILDER clearExports() {
            this.isExportsMutated = true;
            this.exports.clear();
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
            this.isExportsMutated = true;
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
            this.isExportsMutated = true;
            this.exports.addAll(exports);
            return self();
        }

        /**
         * List of exports directives.
         *
         * @param export add single exports
         * @return updated builder instance
         * @see #exports()
         */
        public BUILDER addExport(ModuleInfoExports export) {
            Objects.requireNonNull(export);
            this.exports.add(export);
            this.isExportsMutated = true;
            return self();
        }

        /**
         * List of exports directives.
         *
         * @param consumer consumer of builder for exports
         * @return updated builder instance
         * @see #exports()
         */
        public BUILDER addExport(Consumer<ModuleInfoExports.Builder> consumer) {
            Objects.requireNonNull(consumer);
            var builder = ModuleInfoExports.builder();
            consumer.accept(builder);
            this.addExport(builder.build());
            return self();
        }

        /**
         * Clear all opens.
         *
         * @return updated builder instance
         * @see #opens()
         */
        public BUILDER clearOpens() {
            this.isOpensMutated = true;
            this.opens.clear();
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
            this.isOpensMutated = true;
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
            this.isOpensMutated = true;
            this.opens.addAll(opens);
            return self();
        }

        /**
         * List of opens directives.
         *
         * @param open add single opens
         * @return updated builder instance
         * @see #opens()
         */
        public BUILDER addOpen(ModuleInfoOpens open) {
            Objects.requireNonNull(open);
            this.opens.add(open);
            this.isOpensMutated = true;
            return self();
        }

        /**
         * List of opens directives.
         *
         * @param consumer consumer of builder for opens
         * @return updated builder instance
         * @see #opens()
         */
        public BUILDER addOpen(Consumer<ModuleInfoOpens.Builder> consumer) {
            Objects.requireNonNull(consumer);
            var builder = ModuleInfoOpens.builder();
            consumer.accept(builder);
            this.addOpen(builder.build());
            return self();
        }

        /**
         * Clear all uses.
         *
         * @return updated builder instance
         * @see #uses()
         */
        public BUILDER clearUses() {
            this.isUsesMutated = true;
            this.uses.clear();
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
            this.isUsesMutated = true;
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
            this.isUsesMutated = true;
            this.uses.addAll(uses);
            return self();
        }

        /**
         * List of uses directives.
         *
         * @param use add single uses
         * @return updated builder instance
         * @see #uses()
         */
        public BUILDER addUse(ModuleInfoUses use) {
            Objects.requireNonNull(use);
            this.uses.add(use);
            this.isUsesMutated = true;
            return self();
        }

        /**
         * List of uses directives.
         *
         * @param consumer consumer of builder for uses
         * @return updated builder instance
         * @see #uses()
         */
        public BUILDER addUse(Consumer<ModuleInfoUses.Builder> consumer) {
            Objects.requireNonNull(consumer);
            var builder = ModuleInfoUses.builder();
            consumer.accept(builder);
            this.addUse(builder.build());
            return self();
        }

        /**
         * Clear all provides.
         *
         * @return updated builder instance
         * @see #provides()
         */
        public BUILDER clearProvides() {
            this.isProvidesMutated = true;
            this.provides.clear();
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
            this.isProvidesMutated = true;
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
            this.isProvidesMutated = true;
            this.provides.addAll(provides);
            return self();
        }

        /**
         * List of provides directives.
         *
         * @param provide add single provides
         * @return updated builder instance
         * @see #provides()
         */
        public BUILDER addProvide(ModuleInfoProvides provide) {
            Objects.requireNonNull(provide);
            this.provides.add(provide);
            this.isProvidesMutated = true;
            return self();
        }

        /**
         * List of provides directives.
         *
         * @param consumer consumer of builder for provides
         * @return updated builder instance
         * @see #provides()
         */
        public BUILDER addProvide(Consumer<ModuleInfoProvides.Builder> consumer) {
            Objects.requireNonNull(consumer);
            var builder = ModuleInfoProvides.builder();
            consumer.accept(builder);
            this.addProvide(builder.build());
            return self();
        }

        /**
         * Clear existing value of originatingElement.
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
         * The type of the object depends on the environment we are in - it may be an {@code TypeElement} in annotation processing,
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
         * Clear all annotations.
         *
         * @return updated builder instance
         * @see #annotations()
         */
        public BUILDER clearAnnotations() {
            this.isAnnotationsMutated = true;
            this.annotations.clear();
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
            this.isAnnotationsMutated = true;
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
            this.isAnnotationsMutated = true;
            this.annotations.addAll(annotations);
            return self();
        }

        /**
         * List of declared and known annotations for this element.
         * Note that "known" implies that the annotation is visible, which depends
         * upon the context in which it was build (such as the {@link java.lang.annotation.Retention of the annotation}).
         *
         * @param annotation add single the list of annotations declared on this element
         * @return updated builder instance
         * @see #annotations()
         */
        public BUILDER addAnnotation(Annotation annotation) {
            Objects.requireNonNull(annotation);
            this.annotations.add(annotation);
            this.isAnnotationsMutated = true;
            return self();
        }

        /**
         * List of declared and known annotations for this element.
         * Note that "known" implies that the annotation is visible, which depends
         * upon the context in which it was build (such as the {@link java.lang.annotation.Retention of the annotation}).
         *
         * @param consumer consumer of builder for the list of annotations declared on this element
         * @return updated builder instance
         * @see #annotations()
         */
        public BUILDER addAnnotation(Consumer<Annotation.Builder> consumer) {
            Objects.requireNonNull(consumer);
            var builder = Annotation.builder();
            consumer.accept(builder);
            this.addAnnotation(builder.build());
            return self();
        }

        /**
         * Clear all inheritedAnnotations.
         *
         * @return updated builder instance
         * @see #inheritedAnnotations()
         */
        public BUILDER clearInheritedAnnotations() {
            this.isInheritedAnnotationsMutated = true;
            this.inheritedAnnotations.clear();
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
            this.isInheritedAnnotationsMutated = true;
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
            this.isInheritedAnnotationsMutated = true;
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
         * @param inheritedAnnotation add single list of all meta annotations of this element
         * @return updated builder instance
         * @see #inheritedAnnotations()
         */
        public BUILDER addInheritedAnnotation(Annotation inheritedAnnotation) {
            Objects.requireNonNull(inheritedAnnotation);
            this.inheritedAnnotations.add(inheritedAnnotation);
            this.isInheritedAnnotationsMutated = true;
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
         * @param consumer consumer of builder for list of all meta annotations of this element
         * @return updated builder instance
         * @see #inheritedAnnotations()
         */
        public BUILDER addInheritedAnnotation(Consumer<Annotation.Builder> consumer) {
            Objects.requireNonNull(consumer);
            var builder = Annotation.builder();
            consumer.accept(builder);
            this.addInheritedAnnotation(builder.build());
            return self();
        }

        /**
         * Module name.
         *
         * @return name of this module
         */
        public Optional<String> name() {
            return Optional.ofNullable(name);
        }

        /**
         * Description, such as javadoc, if available.
         *
         * @return description of this element
         */
        public Optional<String> description() {
            return Optional.ofNullable(description);
        }

        /**
         * Whether this is an open module.
         *
         * @return if open
         */
        public boolean isOpen() {
            return isOpen;
        }

        /**
         * List of requires directives.
         *
         * @return requires
         */
        public List<ModuleInfoRequires> requires() {
            return requires;
        }

        /**
         * List of exports directives.
         *
         * @return exports
         */
        public List<ModuleInfoExports> exports() {
            return exports;
        }

        /**
         * List of opens directives.
         *
         * @return opens
         */
        public List<ModuleInfoOpens> opens() {
            return opens;
        }

        /**
         * List of uses directives.
         *
         * @return uses
         */
        public List<ModuleInfoUses> uses() {
            return uses;
        }

        /**
         * List of provides directives.
         *
         * @return provides
         */
        public List<ModuleInfoProvides> provides() {
            return provides;
        }

        /**
         * The element used to create this instance.
         * The type of the object depends on the environment we are in - it may be an {@code TypeElement} in annotation processing,
         * or a {@code ClassInfo} when using classpath scanning.
         *
         * @return originating element
         */
        public Optional<Object> originatingElement() {
            return Optional.ofNullable(originatingElement);
        }

        /**
         * List of declared and known annotations for this element.
         * Note that "known" implies that the annotation is visible, which depends
         * upon the context in which it was build (such as the {@link java.lang.annotation.Retention of the annotation}).
         *
         * @return the list of annotations declared on this element
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
         * @return list of all meta annotations of this element
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
                    + "provides=" + provides
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
            this.description = description.orElse(this.description);
            return self();
        }

        /**
         * The element used to create this instance.
         * The type of the object depends on the environment we are in - it may be an {@code TypeElement} in annotation processing,
         * or a {@code ClassInfo} when using classpath scanning.
         *
         * @param originatingElement originating element
         * @return updated builder instance
         * @see #originatingElement()
         */
        @SuppressWarnings("unchecked")
        BUILDER originatingElement(Optional<?> originatingElement) {
            Objects.requireNonNull(originatingElement);
            this.originatingElement = originatingElement.map(Object.class::cast).orElse(this.originatingElement);
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
                this.description = builder.description().map(Function.identity());
                this.isOpen = builder.isOpen();
                this.requires = List.copyOf(builder.requires());
                this.exports = List.copyOf(builder.exports());
                this.opens = List.copyOf(builder.opens());
                this.uses = List.copyOf(builder.uses());
                this.provides = List.copyOf(builder.provides());
                this.originatingElement = builder.originatingElement().map(Function.identity());
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
                        + "provides=" + provides
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
                    && Objects.equals(provides, other.provides());
            }

            @Override
            public int hashCode() {
                return Objects.hash(name, isOpen, requires, exports, opens, uses, provides);
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
