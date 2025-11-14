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

package io.helidon.builder.codegen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import io.helidon.builder.api.Prototype;
import io.helidon.codegen.classmodel.Javadoc;
import io.helidon.common.Errors;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.Annotated;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;

/**
 * Information about the prototype we are going to build.
 *
 * @see #builder()
 */
public interface PrototypeInfo extends Prototype.Api, Annotated {

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
    static Builder builder(PrototypeInfo instance) {
        return PrototypeInfo.builder().from(instance);
    }

    /**
     * Blueprint type info.
     * A new prototype cannot be generated without a blueprint to base it on, so this is a required option.
     *
     * @return blueprint type information
     */
    TypeInfo blueprint();

    /**
     * If the builder should act as a factory for another type, this is the type.
     * <p>
     * Method {@link io.helidon.builder.api.Prototype.Builder#buildPrototype()} builds the prototype,
     * while method {@link io.helidon.common.Builder#build()} builds the runtime type.
     *
     * @return runtime type, if configured
     */
    Optional<TypeName> runtimeType();

    /**
     * Javadoc for the generated prototype.
     *
     * @return prototype javadoc
     */
    Javadoc javadoc();

    /**
     * Javadoc for the builder base.
     *
     * @return builder base javadoc
     */
    Javadoc builderBaseJavadoc();

    /**
     * Javadoc for the builder class.
     *
     * @return builder javadoc
     */
    Javadoc builderJavadoc();

    /**
     * Builder decorator, if configured.
     *
     * @return type of the builder decorator, if present
     */
    Optional<TypeName> builderDecorator();

    /**
     * Type name of the generated prototype interface.
     * <p>
     * This interface will contain the following inner classes:
     * <ul>
     *     <li>{@code BuilderBase} - base of the builder with all setters, to support prototype inheritance</li>
     *     <li>{@code Builder} - builder extending the builder base that builds the prototype instance</li>
     *     <li>implementation - prototype implementation class, supports inheritance as well</li>
     * </ul>
     *
     * If you modify the type name to be generated, the result will not support inheritance.
     *
     * @return type of the prototype interface
     */
    TypeName prototypeType();

    /**
     * A predicate to include possible interface default methods as options.
     * The default behavior is to exclude all default methods.
     * <p>
     * Sequence of checking if a default method should be an option method:
     * <nl>
     * <li>Check the method signature (i.e. {@code process(java.lang.String)}, if accepted, use it as an option</li>
     * <li>Check the method name (i.e. {@code process}, if accepted, use it as an option</li>
     * <li>Otherwise the default method will not be an option</li>
     * </nl>
     *
     * @return predicate for method names
     */
    Predicate<String> defaultMethodsPredicate();

    /**
     * Access modifier for the generated prototype.
     *
     * @return access modifier, defaults to {@code public}
     */
    AccessModifier accessModifier();

    /**
     * Access modifier for the generated builder.
     *
     * @return access modifier, defaults to {@code public}
     */
    AccessModifier builderAccessModifier();

    /**
     * Whether to create an empty {@code create()} method.
     *
     * @return whether to create an empty {@code create()} method, defaults to {@code true}
     */
    boolean createEmptyCreate();

    /**
     * Whether to use record style or bean style accessors.
     * <p>
     * Let's consider option {@code accessModifier} of type {@code AccessModifier}.
     * <p>
     * Record style:
     * <ul>
     *     <li>Getter: {@code AccessModifier accessModifier()}</li>
     *     <li>Setter: {@code Builder accessModifier(AccessModifier)}</li>
     * </ul>
     * Bean style:
     * <ul>
     *     <li>Getter: {@code AccessModifier getAccessModifier()}</li>
     *     <li>Setter: {@code Builder setAccessModifier(AccessModifier)}</li>
     * </ul>
     *
     * @return whether to use record style accessors, defaults to {@code true}
     */
    boolean recordStyle();

    /**
     * Prototype configuration details.
     *
     * @return prototype configuration details, if configured
     */
    Optional<PrototypeConfigured> configured();

    /**
     * Whether to use the service registry to discover providers.
     *
     * @return whether to support service registry, defaults to {@code false}
     */
    boolean registrySupport();

    /**
     * Whether to detach the blueprint from the generated prototype.
     *
     * @return true if the blueprint should not be extended by the prototype
     */
    boolean detachBlueprint();

    /**
     * If the blueprint extends an existing prototype (or blueprint), we must extend that prototype and
     * also that prototype's builder.
     *
     * @return super prototype, if present
     */
    Optional<TypeName> superPrototype();

    /**
     * List of types the prototype should extend.
     * This list will always contain the blueprint interface, and {@link io.helidon.builder.api.Prototype.Api}.
     * This list also contains {@link #superPrototype()} if present.
     *
     * @return types the prototype must extend
     */
    Set<TypeName> superTypes();

    /**
     * Types the generated prototype should provide, if this prototype is/configures a service provider.
     *
     * @return provider provides types
     */
    Set<TypeName> providerProvides();

    /**
     * Constants to be defined on the prototype.
     * A constant may be either a reference to another constant or a generated value.
     *
     * @return constants to add to the prototype
     */
    List<PrototypeConstant> constants();

    /**
     * Additional methods to be added to the prototype as default methods.
     * <p>
     * Non-default interface methods cannot be added, as the implementation is not customizable.
     * This list does NOT contain option methods.
     *
     * @return custom methods to add to the prototype
     */
    List<GeneratedMethod> prototypeMethods();

    /**
     * Factory methods to be added to the prototype.
     * A static method annotated with {@code io.helidon.builder.api.Prototype.FactoryMethod} will be either added here,
     * or to #factoryMethods() depending on signature.
     *
     * @return a list of factory methods to add to the prototype
     */
    List<GeneratedMethod> prototypeFactoryMethods();

    /**
     * Additional methods to be added to the prototype builder base.
     * It is your responsibility to ensure these methods do not conflict with option methods.
     * This list does NOT contain option methods.
     *
     * @return custom methods to add to the prototype builder base
     */
    List<GeneratedMethod> builderMethods();

    /**
     * Factory methods to be used when mapping config to types.
     * These methods will never be made public.
     *
     * @return factory methods to use when mapping config to types
     */
    List<FactoryMethod> factoryMethods();

    /**
     * Fluent API builder base for {@link io.helidon.builder.codegen.PrototypeInfo}.
     *
     * @param <BUILDER>   type of the builder extending this abstract builder
     * @param <PROTOTYPE> type of the prototype interface that would be built by {@link #buildPrototype()}
     */
    abstract class BuilderBase<BUILDER extends BuilderBase<BUILDER, PROTOTYPE>, PROTOTYPE extends PrototypeInfo>
            implements Prototype.Builder<BUILDER, PROTOTYPE> {

        private final List<GeneratedMethod> builderMethods = new ArrayList<>();
        private final List<GeneratedMethod> prototypeFactoryMethods = new ArrayList<>();
        private final List<GeneratedMethod> prototypeMethods = new ArrayList<>();
        private final List<PrototypeConstant> constants = new ArrayList<>();
        private final List<FactoryMethod> factoryMethods = new ArrayList<>();
        private final List<Annotation> annotations = new ArrayList<>();
        private final List<Annotation> inheritedAnnotations = new ArrayList<>();
        private final Set<TypeName> providerProvides = new LinkedHashSet<>();
        private final Set<TypeName> superTypes = new LinkedHashSet<>();
        private AccessModifier accessModifier = AccessModifier.PUBLIC;
        private AccessModifier builderAccessModifier = AccessModifier.PUBLIC;
        private boolean createEmptyCreate = true;
        private boolean detachBlueprint = false;
        private boolean isAnnotationsMutated;
        private boolean isBuilderMethodsMutated;
        private boolean isConstantsMutated;
        private boolean isFactoryMethodsMutated;
        private boolean isInheritedAnnotationsMutated;
        private boolean isPrototypeFactoryMethodsMutated;
        private boolean isPrototypeMethodsMutated;
        private boolean isProviderProvidesMutated;
        private boolean isSuperTypesMutated;
        private boolean recordStyle = true;
        private boolean registrySupport;
        private Javadoc builderBaseJavadoc;
        private Javadoc builderJavadoc;
        private Javadoc javadoc;
        private Predicate<String> defaultMethodsPredicate = it -> false;
        private PrototypeConfigured configured;
        private TypeInfo blueprint;
        private TypeName builderDecorator;
        private TypeName prototypeType;
        private TypeName runtimeType;
        private TypeName superPrototype;

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
        public BUILDER from(PrototypeInfo prototype) {
            blueprint(prototype.blueprint());
            runtimeType(prototype.runtimeType());
            javadoc(prototype.javadoc());
            builderBaseJavadoc(prototype.builderBaseJavadoc());
            builderJavadoc(prototype.builderJavadoc());
            builderDecorator(prototype.builderDecorator());
            prototypeType(prototype.prototypeType());
            defaultMethodsPredicate(prototype.defaultMethodsPredicate());
            accessModifier(prototype.accessModifier());
            builderAccessModifier(prototype.builderAccessModifier());
            createEmptyCreate(prototype.createEmptyCreate());
            recordStyle(prototype.recordStyle());
            configured(prototype.configured());
            registrySupport(prototype.registrySupport());
            detachBlueprint(prototype.detachBlueprint());
            superPrototype(prototype.superPrototype());
            if (!this.isSuperTypesMutated) {
                this.superTypes.clear();
            }
            addSuperTypes(prototype.superTypes());
            if (!this.isProviderProvidesMutated) {
                this.providerProvides.clear();
            }
            addProviderProvides(prototype.providerProvides());
            if (!this.isConstantsMutated) {
                this.constants.clear();
            }
            addConstants(prototype.constants());
            if (!this.isPrototypeMethodsMutated) {
                this.prototypeMethods.clear();
            }
            addPrototypeMethods(prototype.prototypeMethods());
            if (!this.isPrototypeFactoryMethodsMutated) {
                this.prototypeFactoryMethods.clear();
            }
            addPrototypeFactoryMethods(prototype.prototypeFactoryMethods());
            if (!this.isBuilderMethodsMutated) {
                this.builderMethods.clear();
            }
            addBuilderMethods(prototype.builderMethods());
            if (!this.isFactoryMethodsMutated) {
                this.factoryMethods.clear();
            }
            addFactoryMethods(prototype.factoryMethods());
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
        public BUILDER from(BuilderBase<?, ?> builder) {
            builder.blueprint().ifPresent(this::blueprint);
            builder.runtimeType().ifPresent(this::runtimeType);
            builder.javadoc().ifPresent(this::javadoc);
            builder.builderBaseJavadoc().ifPresent(this::builderBaseJavadoc);
            builder.builderJavadoc().ifPresent(this::builderJavadoc);
            builder.builderDecorator().ifPresent(this::builderDecorator);
            builder.prototypeType().ifPresent(this::prototypeType);
            defaultMethodsPredicate(builder.defaultMethodsPredicate());
            accessModifier(builder.accessModifier());
            builderAccessModifier(builder.builderAccessModifier());
            createEmptyCreate(builder.createEmptyCreate());
            recordStyle(builder.recordStyle());
            builder.configured().ifPresent(this::configured);
            registrySupport(builder.registrySupport());
            detachBlueprint(builder.detachBlueprint());
            builder.superPrototype().ifPresent(this::superPrototype);
            if (this.isSuperTypesMutated) {
                if (builder.isSuperTypesMutated) {
                    addSuperTypes(builder.superTypes);
                }
            } else {
                superTypes(builder.superTypes);
            }
            if (this.isProviderProvidesMutated) {
                if (builder.isProviderProvidesMutated) {
                    addProviderProvides(builder.providerProvides);
                }
            } else {
                providerProvides(builder.providerProvides);
            }
            if (this.isConstantsMutated) {
                if (builder.isConstantsMutated) {
                    addConstants(builder.constants);
                }
            } else {
                constants(builder.constants);
            }
            if (this.isPrototypeMethodsMutated) {
                if (builder.isPrototypeMethodsMutated) {
                    addPrototypeMethods(builder.prototypeMethods);
                }
            } else {
                prototypeMethods(builder.prototypeMethods);
            }
            if (this.isPrototypeFactoryMethodsMutated) {
                if (builder.isPrototypeFactoryMethodsMutated) {
                    addPrototypeFactoryMethods(builder.prototypeFactoryMethods);
                }
            } else {
                prototypeFactoryMethods(builder.prototypeFactoryMethods);
            }
            if (this.isBuilderMethodsMutated) {
                if (builder.isBuilderMethodsMutated) {
                    addBuilderMethods(builder.builderMethods);
                }
            } else {
                builderMethods(builder.builderMethods);
            }
            if (this.isFactoryMethodsMutated) {
                if (builder.isFactoryMethodsMutated) {
                    addFactoryMethods(builder.factoryMethods);
                }
            } else {
                factoryMethods(builder.factoryMethods);
            }
            if (this.isAnnotationsMutated) {
                if (builder.isAnnotationsMutated) {
                    addAnnotations(builder.annotations);
                }
            } else {
                annotations(builder.annotations);
            }
            if (this.isInheritedAnnotationsMutated) {
                if (builder.isInheritedAnnotationsMutated) {
                    addInheritedAnnotations(builder.inheritedAnnotations);
                }
            } else {
                inheritedAnnotations(builder.inheritedAnnotations);
            }
            return self();
        }

        /**
         * Blueprint type info.
         * A new prototype cannot be generated without a blueprint to base it on, so this is a required option.
         *
         * @param blueprint blueprint type information
         * @return updated builder instance
         * @see #blueprint()
         */
        public BUILDER blueprint(TypeInfo blueprint) {
            Objects.requireNonNull(blueprint);
            this.blueprint = blueprint;
            return self();
        }

        /**
         * Blueprint type info.
         * A new prototype cannot be generated without a blueprint to base it on, so this is a required option.
         *
         * @param consumer consumer of builder of blueprint type information
         * @return updated builder instance
         * @see #blueprint()
         */
        public BUILDER blueprint(Consumer<TypeInfo.Builder> consumer) {
            Objects.requireNonNull(consumer);
            var builder = TypeInfo.builder();
            consumer.accept(builder);
            this.blueprint(builder.build());
            return self();
        }

        /**
         * Blueprint type info.
         * A new prototype cannot be generated without a blueprint to base it on, so this is a required option.
         *
         * @param supplier supplier of blueprint type information
         * @return updated builder instance
         * @see #blueprint()
         */
        public BUILDER blueprint(Supplier<? extends TypeInfo> supplier) {
            Objects.requireNonNull(supplier);
            this.blueprint(supplier.get());
            return self();
        }

        /**
         * Clear existing value of runtimeType.
         *
         * @return updated builder instance
         * @see #runtimeType()
         */
        public BUILDER clearRuntimeType() {
            this.runtimeType = null;
            return self();
        }

        /**
         * If the builder should act as a factory for another type, this is the type.
         * <p>
         * Method {@link io.helidon.builder.api.Prototype.Builder#buildPrototype()} builds the prototype,
         * while method {@link io.helidon.common.Builder#build()} builds the runtime type.
         *
         * @param runtimeType runtime type, if configured
         * @return updated builder instance
         * @see #runtimeType()
         */
        public BUILDER runtimeType(TypeName runtimeType) {
            Objects.requireNonNull(runtimeType);
            this.runtimeType = runtimeType;
            return self();
        }

        /**
         * If the builder should act as a factory for another type, this is the type.
         * <p>
         * Method {@link io.helidon.builder.api.Prototype.Builder#buildPrototype()} builds the prototype,
         * while method {@link io.helidon.common.Builder#build()} builds the runtime type.
         *
         * @param consumer consumer of builder of runtime type, if configured
         * @return updated builder instance
         * @see #runtimeType()
         */
        public BUILDER runtimeType(Consumer<TypeName.Builder> consumer) {
            Objects.requireNonNull(consumer);
            var builder = TypeName.builder();
            consumer.accept(builder);
            this.runtimeType(builder.build());
            return self();
        }

        /**
         * If the builder should act as a factory for another type, this is the type.
         * <p>
         * Method {@link io.helidon.builder.api.Prototype.Builder#buildPrototype()} builds the prototype,
         * while method {@link io.helidon.common.Builder#build()} builds the runtime type.
         *
         * @param supplier supplier of runtime type, if configured
         * @return updated builder instance
         * @see #runtimeType()
         */
        public BUILDER runtimeType(Supplier<? extends TypeName> supplier) {
            Objects.requireNonNull(supplier);
            this.runtimeType(supplier.get());
            return self();
        }

        /**
         * Javadoc for the generated prototype.
         *
         * @param javadoc prototype javadoc
         * @return updated builder instance
         * @see #javadoc()
         */
        public BUILDER javadoc(Javadoc javadoc) {
            Objects.requireNonNull(javadoc);
            this.javadoc = javadoc;
            return self();
        }

        /**
         * Javadoc for the generated prototype.
         *
         * @param consumer consumer of builder of prototype javadoc
         * @return updated builder instance
         * @see #javadoc()
         */
        public BUILDER javadoc(Consumer<Javadoc.Builder> consumer) {
            Objects.requireNonNull(consumer);
            var builder = Javadoc.builder();
            consumer.accept(builder);
            this.javadoc(builder.build());
            return self();
        }

        /**
         * Javadoc for the generated prototype.
         *
         * @param supplier supplier of prototype javadoc
         * @return updated builder instance
         * @see #javadoc()
         */
        public BUILDER javadoc(Supplier<? extends Javadoc> supplier) {
            Objects.requireNonNull(supplier);
            this.javadoc(supplier.get());
            return self();
        }

        /**
         * Javadoc for the builder base.
         *
         * @param builderBaseJavadoc builder base javadoc
         * @return updated builder instance
         * @see #builderBaseJavadoc()
         */
        public BUILDER builderBaseJavadoc(Javadoc builderBaseJavadoc) {
            Objects.requireNonNull(builderBaseJavadoc);
            this.builderBaseJavadoc = builderBaseJavadoc;
            return self();
        }

        /**
         * Javadoc for the builder base.
         *
         * @param consumer consumer of builder of builder base javadoc
         * @return updated builder instance
         * @see #builderBaseJavadoc()
         */
        public BUILDER builderBaseJavadoc(Consumer<Javadoc.Builder> consumer) {
            Objects.requireNonNull(consumer);
            var builder = Javadoc.builder();
            consumer.accept(builder);
            this.builderBaseJavadoc(builder.build());
            return self();
        }

        /**
         * Javadoc for the builder base.
         *
         * @param supplier supplier of builder base javadoc
         * @return updated builder instance
         * @see #builderBaseJavadoc()
         */
        public BUILDER builderBaseJavadoc(Supplier<? extends Javadoc> supplier) {
            Objects.requireNonNull(supplier);
            this.builderBaseJavadoc(supplier.get());
            return self();
        }

        /**
         * Javadoc for the builder class.
         *
         * @param builderJavadoc builder javadoc
         * @return updated builder instance
         * @see #builderJavadoc()
         */
        public BUILDER builderJavadoc(Javadoc builderJavadoc) {
            Objects.requireNonNull(builderJavadoc);
            this.builderJavadoc = builderJavadoc;
            return self();
        }

        /**
         * Javadoc for the builder class.
         *
         * @param consumer consumer of builder of builder javadoc
         * @return updated builder instance
         * @see #builderJavadoc()
         */
        public BUILDER builderJavadoc(Consumer<Javadoc.Builder> consumer) {
            Objects.requireNonNull(consumer);
            var builder = Javadoc.builder();
            consumer.accept(builder);
            this.builderJavadoc(builder.build());
            return self();
        }

        /**
         * Javadoc for the builder class.
         *
         * @param supplier supplier of builder javadoc
         * @return updated builder instance
         * @see #builderJavadoc()
         */
        public BUILDER builderJavadoc(Supplier<? extends Javadoc> supplier) {
            Objects.requireNonNull(supplier);
            this.builderJavadoc(supplier.get());
            return self();
        }

        /**
         * Clear existing value of builderDecorator.
         *
         * @return updated builder instance
         * @see #builderDecorator()
         */
        public BUILDER clearBuilderDecorator() {
            this.builderDecorator = null;
            return self();
        }

        /**
         * Builder decorator, if configured.
         *
         * @param builderDecorator type of the builder decorator, if present
         * @return updated builder instance
         * @see #builderDecorator()
         */
        public BUILDER builderDecorator(TypeName builderDecorator) {
            Objects.requireNonNull(builderDecorator);
            this.builderDecorator = builderDecorator;
            return self();
        }

        /**
         * Builder decorator, if configured.
         *
         * @param consumer consumer of builder of type of the builder decorator, if present
         * @return updated builder instance
         * @see #builderDecorator()
         */
        public BUILDER builderDecorator(Consumer<TypeName.Builder> consumer) {
            Objects.requireNonNull(consumer);
            var builder = TypeName.builder();
            consumer.accept(builder);
            this.builderDecorator(builder.build());
            return self();
        }

        /**
         * Builder decorator, if configured.
         *
         * @param supplier supplier of type of the builder decorator, if present
         * @return updated builder instance
         * @see #builderDecorator()
         */
        public BUILDER builderDecorator(Supplier<? extends TypeName> supplier) {
            Objects.requireNonNull(supplier);
            this.builderDecorator(supplier.get());
            return self();
        }

        /**
         * Type name of the generated prototype interface.
         * <p>
         * This interface will contain the following inner classes:
         * <ul>
         *     <li>{@code BuilderBase} - base of the builder with all setters, to support prototype inheritance</li>
         *     <li>{@code Builder} - builder extending the builder base that builds the prototype instance</li>
         *     <li>implementation - prototype implementation class, supports inheritance as well</li>
         * </ul>
         *
         * If you modify the type name to be generated, the result will not support inheritance.
         *
         * @param prototypeType type of the prototype interface
         * @return updated builder instance
         * @see #prototypeType()
         */
        public BUILDER prototypeType(TypeName prototypeType) {
            Objects.requireNonNull(prototypeType);
            this.prototypeType = prototypeType;
            return self();
        }

        /**
         * Type name of the generated prototype interface.
         * <p>
         * This interface will contain the following inner classes:
         * <ul>
         *     <li>{@code BuilderBase} - base of the builder with all setters, to support prototype inheritance</li>
         *     <li>{@code Builder} - builder extending the builder base that builds the prototype instance</li>
         *     <li>implementation - prototype implementation class, supports inheritance as well</li>
         * </ul>
         *
         * If you modify the type name to be generated, the result will not support inheritance.
         *
         * @param consumer consumer of builder of type of the prototype interface
         * @return updated builder instance
         * @see #prototypeType()
         */
        public BUILDER prototypeType(Consumer<TypeName.Builder> consumer) {
            Objects.requireNonNull(consumer);
            var builder = TypeName.builder();
            consumer.accept(builder);
            this.prototypeType(builder.build());
            return self();
        }

        /**
         * Type name of the generated prototype interface.
         * <p>
         * This interface will contain the following inner classes:
         * <ul>
         *     <li>{@code BuilderBase} - base of the builder with all setters, to support prototype inheritance</li>
         *     <li>{@code Builder} - builder extending the builder base that builds the prototype instance</li>
         *     <li>implementation - prototype implementation class, supports inheritance as well</li>
         * </ul>
         *
         * If you modify the type name to be generated, the result will not support inheritance.
         *
         * @param supplier supplier of type of the prototype interface
         * @return updated builder instance
         * @see #prototypeType()
         */
        public BUILDER prototypeType(Supplier<? extends TypeName> supplier) {
            Objects.requireNonNull(supplier);
            this.prototypeType(supplier.get());
            return self();
        }

        /**
         * A predicate to include possible interface default methods as options.
         * The default behavior is to exclude all default methods.
         * <p>
         * Sequence of checking if a default method should be an option method:
         * <nl>
         * <li>Check the method signature (i.e. {@code process(java.lang.String)}, if accepted, use it as an option</li>
         * <li>Check the method name (i.e. {@code process}, if accepted, use it as an option</li>
         * <li>Otherwise the default method will not be an option</li>
         * </nl>
         *
         * @param defaultMethodsPredicate predicate for method names
         * @return updated builder instance
         * @see #defaultMethodsPredicate()
         */
        public BUILDER defaultMethodsPredicate(Predicate<String> defaultMethodsPredicate) {
            Objects.requireNonNull(defaultMethodsPredicate);
            this.defaultMethodsPredicate = defaultMethodsPredicate;
            return self();
        }

        /**
         * Access modifier for the generated prototype.
         *
         * @param accessModifier access modifier, defaults to {@code public}
         * @return updated builder instance
         * @see #accessModifier()
         */
        public BUILDER accessModifier(AccessModifier accessModifier) {
            Objects.requireNonNull(accessModifier);
            this.accessModifier = accessModifier;
            return self();
        }

        /**
         * Access modifier for the generated builder.
         *
         * @param builderAccessModifier access modifier, defaults to {@code public}
         * @return updated builder instance
         * @see #builderAccessModifier()
         */
        public BUILDER builderAccessModifier(AccessModifier builderAccessModifier) {
            Objects.requireNonNull(builderAccessModifier);
            this.builderAccessModifier = builderAccessModifier;
            return self();
        }

        /**
         * Whether to create an empty {@code create()} method.
         *
         * @param createEmptyCreate whether to create an empty {@code create()} method, defaults to {@code true}
         * @return updated builder instance
         * @see #createEmptyCreate()
         */
        public BUILDER createEmptyCreate(boolean createEmptyCreate) {
            this.createEmptyCreate = createEmptyCreate;
            return self();
        }

        /**
         * Whether to use record style or bean style accessors.
         * <p>
         * Let's consider option {@code accessModifier} of type {@code AccessModifier}.
         * <p>
         * Record style:
         * <ul>
         *     <li>Getter: {@code AccessModifier accessModifier()}</li>
         *     <li>Setter: {@code Builder accessModifier(AccessModifier)}</li>
         * </ul>
         * Bean style:
         * <ul>
         *     <li>Getter: {@code AccessModifier getAccessModifier()}</li>
         *     <li>Setter: {@code Builder setAccessModifier(AccessModifier)}</li>
         * </ul>
         *
         * @param recordStyle whether to use record style accessors, defaults to {@code true}
         * @return updated builder instance
         * @see #recordStyle()
         */
        public BUILDER recordStyle(boolean recordStyle) {
            this.recordStyle = recordStyle;
            return self();
        }

        /**
         * Clear existing value of configured.
         *
         * @return updated builder instance
         * @see #configured()
         */
        public BUILDER clearConfigured() {
            this.configured = null;
            return self();
        }

        /**
         * Prototype configuration details.
         *
         * @param configured prototype configuration details, if configured
         * @return updated builder instance
         * @see #configured()
         */
        public BUILDER configured(PrototypeConfigured configured) {
            Objects.requireNonNull(configured);
            this.configured = configured;
            return self();
        }

        /**
         * Prototype configuration details.
         *
         * @param consumer consumer of builder of prototype configuration details, if configured
         * @return updated builder instance
         * @see #configured()
         */
        public BUILDER configured(Consumer<PrototypeConfigured.Builder> consumer) {
            Objects.requireNonNull(consumer);
            var builder = PrototypeConfigured.builder();
            consumer.accept(builder);
            this.configured(builder.build());
            return self();
        }

        /**
         * Prototype configuration details.
         *
         * @param supplier supplier of prototype configuration details, if configured
         * @return updated builder instance
         * @see #configured()
         */
        public BUILDER configured(Supplier<? extends PrototypeConfigured> supplier) {
            Objects.requireNonNull(supplier);
            this.configured(supplier.get());
            return self();
        }

        /**
         * Whether to use the service registry to discover providers.
         *
         * @param registrySupport whether to support service registry, defaults to {@code false}
         * @return updated builder instance
         * @see #registrySupport()
         */
        public BUILDER registrySupport(boolean registrySupport) {
            this.registrySupport = registrySupport;
            return self();
        }

        /**
         * Whether to detach the blueprint from the generated prototype.
         *
         * @param detachBlueprint true if the blueprint should not be extended by the prototype
         * @return updated builder instance
         * @see #detachBlueprint()
         */
        public BUILDER detachBlueprint(boolean detachBlueprint) {
            this.detachBlueprint = detachBlueprint;
            return self();
        }

        /**
         * Clear existing value of superPrototype.
         *
         * @return updated builder instance
         * @see #superPrototype()
         */
        public BUILDER clearSuperPrototype() {
            this.superPrototype = null;
            return self();
        }

        /**
         * If the blueprint extends an existing prototype (or blueprint), we must extend that prototype and
         * also that prototype's builder.
         *
         * @param superPrototype super prototype, if present
         * @return updated builder instance
         * @see #superPrototype()
         */
        public BUILDER superPrototype(TypeName superPrototype) {
            Objects.requireNonNull(superPrototype);
            this.superPrototype = superPrototype;
            return self();
        }

        /**
         * If the blueprint extends an existing prototype (or blueprint), we must extend that prototype and
         * also that prototype's builder.
         *
         * @param consumer consumer of builder of super prototype, if present
         * @return updated builder instance
         * @see #superPrototype()
         */
        public BUILDER superPrototype(Consumer<TypeName.Builder> consumer) {
            Objects.requireNonNull(consumer);
            var builder = TypeName.builder();
            consumer.accept(builder);
            this.superPrototype(builder.build());
            return self();
        }

        /**
         * If the blueprint extends an existing prototype (or blueprint), we must extend that prototype and
         * also that prototype's builder.
         *
         * @param supplier supplier of super prototype, if present
         * @return updated builder instance
         * @see #superPrototype()
         */
        public BUILDER superPrototype(Supplier<? extends TypeName> supplier) {
            Objects.requireNonNull(supplier);
            this.superPrototype(supplier.get());
            return self();
        }

        /**
         * Clear all superTypes.
         *
         * @return updated builder instance
         * @see #superTypes()
         */
        public BUILDER clearSuperTypes() {
            this.isSuperTypesMutated = true;
            this.superTypes.clear();
            return self();
        }

        /**
         * List of types the prototype should extend.
         * This list will always contain the blueprint interface, and {@link io.helidon.builder.api.Prototype.Api}.
         * This list also contains {@link #superPrototype()} if present.
         *
         * @param superTypes types the prototype must extend
         * @return updated builder instance
         * @see #superTypes()
         */
        public BUILDER superTypes(Set<? extends TypeName> superTypes) {
            Objects.requireNonNull(superTypes);
            this.isSuperTypesMutated = true;
            this.superTypes.clear();
            this.superTypes.addAll(superTypes);
            return self();
        }

        /**
         * List of types the prototype should extend.
         * This list will always contain the blueprint interface, and {@link io.helidon.builder.api.Prototype.Api}.
         * This list also contains {@link #superPrototype()} if present.
         *
         * @param superTypes types the prototype must extend
         * @return updated builder instance
         * @see #superTypes()
         */
        public BUILDER addSuperTypes(Set<? extends TypeName> superTypes) {
            Objects.requireNonNull(superTypes);
            this.isSuperTypesMutated = true;
            this.superTypes.addAll(superTypes);
            return self();
        }

        /**
         * List of types the prototype should extend.
         * This list will always contain the blueprint interface, and {@link io.helidon.builder.api.Prototype.Api}.
         * This list also contains {@link #superPrototype()} if present.
         *
         * @param superType add single types the prototype must extend
         * @return updated builder instance
         * @see #superTypes()
         */
        public BUILDER addSuperType(TypeName superType) {
            Objects.requireNonNull(superType);
            this.superTypes.add(superType);
            this.isSuperTypesMutated = true;
            return self();
        }

        /**
         * List of types the prototype should extend.
         * This list will always contain the blueprint interface, and {@link io.helidon.builder.api.Prototype.Api}.
         * This list also contains {@link #superPrototype()} if present.
         *
         * @param consumer consumer of builder for types the prototype must extend
         * @return updated builder instance
         * @see #superTypes()
         */
        public BUILDER addSuperType(Consumer<TypeName.Builder> consumer) {
            Objects.requireNonNull(consumer);
            var builder = TypeName.builder();
            consumer.accept(builder);
            this.addSuperType(builder.build());
            return self();
        }

        /**
         * Clear all providerProvides.
         *
         * @return updated builder instance
         * @see #providerProvides()
         */
        public BUILDER clearProviderProvides() {
            this.isProviderProvidesMutated = true;
            this.providerProvides.clear();
            return self();
        }

        /**
         * Types the generated prototype should provide, if this prototype is/configures a service provider.
         *
         * @param providerProvides provider provides types
         * @return updated builder instance
         * @see #providerProvides()
         */
        public BUILDER providerProvides(Set<? extends TypeName> providerProvides) {
            Objects.requireNonNull(providerProvides);
            this.isProviderProvidesMutated = true;
            this.providerProvides.clear();
            this.providerProvides.addAll(providerProvides);
            return self();
        }

        /**
         * Types the generated prototype should provide, if this prototype is/configures a service provider.
         *
         * @param providerProvides provider provides types
         * @return updated builder instance
         * @see #providerProvides()
         */
        public BUILDER addProviderProvides(Set<? extends TypeName> providerProvides) {
            Objects.requireNonNull(providerProvides);
            this.isProviderProvidesMutated = true;
            this.providerProvides.addAll(providerProvides);
            return self();
        }

        /**
         * Types the generated prototype should provide, if this prototype is/configures a service provider.
         *
         * @param providerProvide add single provider provides types
         * @return updated builder instance
         * @see #providerProvides()
         */
        public BUILDER addProviderProvide(TypeName providerProvide) {
            Objects.requireNonNull(providerProvide);
            this.providerProvides.add(providerProvide);
            this.isProviderProvidesMutated = true;
            return self();
        }

        /**
         * Types the generated prototype should provide, if this prototype is/configures a service provider.
         *
         * @param consumer consumer of builder for provider provides types
         * @return updated builder instance
         * @see #providerProvides()
         */
        public BUILDER addProviderProvide(Consumer<TypeName.Builder> consumer) {
            Objects.requireNonNull(consumer);
            var builder = TypeName.builder();
            consumer.accept(builder);
            this.addProviderProvide(builder.build());
            return self();
        }

        /**
         * Clear all constants.
         *
         * @return updated builder instance
         * @see #constants()
         */
        public BUILDER clearConstants() {
            this.isConstantsMutated = true;
            this.constants.clear();
            return self();
        }

        /**
         * Constants to be defined on the prototype.
         * A constant may be either a reference to another constant or a generated value.
         *
         * @param constants constants to add to the prototype
         * @return updated builder instance
         * @see #constants()
         */
        public BUILDER constants(List<? extends PrototypeConstant> constants) {
            Objects.requireNonNull(constants);
            this.isConstantsMutated = true;
            this.constants.clear();
            this.constants.addAll(constants);
            return self();
        }

        /**
         * Constants to be defined on the prototype.
         * A constant may be either a reference to another constant or a generated value.
         *
         * @param constants constants to add to the prototype
         * @return updated builder instance
         * @see #constants()
         */
        public BUILDER addConstants(List<? extends PrototypeConstant> constants) {
            Objects.requireNonNull(constants);
            this.isConstantsMutated = true;
            this.constants.addAll(constants);
            return self();
        }

        /**
         * Constants to be defined on the prototype.
         * A constant may be either a reference to another constant or a generated value.
         *
         * @param constant add single constants to add to the prototype
         * @return updated builder instance
         * @see #constants()
         */
        public BUILDER addConstant(PrototypeConstant constant) {
            Objects.requireNonNull(constant);
            this.constants.add(constant);
            this.isConstantsMutated = true;
            return self();
        }

        /**
         * Constants to be defined on the prototype.
         * A constant may be either a reference to another constant or a generated value.
         *
         * @param consumer consumer of builder for constants to add to the prototype
         * @return updated builder instance
         * @see #constants()
         */
        public BUILDER addConstant(Consumer<PrototypeConstant.Builder> consumer) {
            Objects.requireNonNull(consumer);
            var builder = PrototypeConstant.builder();
            consumer.accept(builder);
            this.addConstant(builder.build());
            return self();
        }

        /**
         * Clear all prototypeMethods.
         *
         * @return updated builder instance
         * @see #prototypeMethods()
         */
        public BUILDER clearPrototypeMethods() {
            this.isPrototypeMethodsMutated = true;
            this.prototypeMethods.clear();
            return self();
        }

        /**
         * Additional methods to be added to the prototype as default methods.
         * <p>
         * Non-default interface methods cannot be added, as the implementation is not customizable.
         * This list does NOT contain option methods.
         *
         * @param prototypeMethods custom methods to add to the prototype
         * @return updated builder instance
         * @see #prototypeMethods()
         */
        public BUILDER prototypeMethods(List<? extends GeneratedMethod> prototypeMethods) {
            Objects.requireNonNull(prototypeMethods);
            this.isPrototypeMethodsMutated = true;
            this.prototypeMethods.clear();
            this.prototypeMethods.addAll(prototypeMethods);
            return self();
        }

        /**
         * Additional methods to be added to the prototype as default methods.
         * <p>
         * Non-default interface methods cannot be added, as the implementation is not customizable.
         * This list does NOT contain option methods.
         *
         * @param prototypeMethods custom methods to add to the prototype
         * @return updated builder instance
         * @see #prototypeMethods()
         */
        public BUILDER addPrototypeMethods(List<? extends GeneratedMethod> prototypeMethods) {
            Objects.requireNonNull(prototypeMethods);
            this.isPrototypeMethodsMutated = true;
            this.prototypeMethods.addAll(prototypeMethods);
            return self();
        }

        /**
         * Additional methods to be added to the prototype as default methods.
         * <p>
         * Non-default interface methods cannot be added, as the implementation is not customizable.
         * This list does NOT contain option methods.
         *
         * @param prototypeMethod add single custom methods to add to the prototype
         * @return updated builder instance
         * @see #prototypeMethods()
         */
        public BUILDER addPrototypeMethod(GeneratedMethod prototypeMethod) {
            Objects.requireNonNull(prototypeMethod);
            this.prototypeMethods.add(prototypeMethod);
            this.isPrototypeMethodsMutated = true;
            return self();
        }

        /**
         * Additional methods to be added to the prototype as default methods.
         * <p>
         * Non-default interface methods cannot be added, as the implementation is not customizable.
         * This list does NOT contain option methods.
         *
         * @param consumer consumer of builder for custom methods to add to the prototype
         * @return updated builder instance
         * @see #prototypeMethods()
         */
        public BUILDER addPrototypeMethod(Consumer<GeneratedMethod.Builder> consumer) {
            Objects.requireNonNull(consumer);
            var builder = GeneratedMethod.builder();
            consumer.accept(builder);
            this.addPrototypeMethod(builder.build());
            return self();
        }

        /**
         * Clear all prototypeFactoryMethods.
         *
         * @return updated builder instance
         * @see #prototypeFactoryMethods()
         */
        public BUILDER clearPrototypeFactoryMethods() {
            this.isPrototypeFactoryMethodsMutated = true;
            this.prototypeFactoryMethods.clear();
            return self();
        }

        /**
         * Factory methods to be added to the prototype.
         * A static method annotated with {@code io.helidon.builder.api.Prototype.FactoryMethod} will be either added here,
         * or to #factoryMethods() depending on signature.
         *
         * @param prototypeFactoryMethods a list of factory methods to add to the prototype
         * @return updated builder instance
         * @see #prototypeFactoryMethods()
         */
        public BUILDER prototypeFactoryMethods(List<? extends GeneratedMethod> prototypeFactoryMethods) {
            Objects.requireNonNull(prototypeFactoryMethods);
            this.isPrototypeFactoryMethodsMutated = true;
            this.prototypeFactoryMethods.clear();
            this.prototypeFactoryMethods.addAll(prototypeFactoryMethods);
            return self();
        }

        /**
         * Factory methods to be added to the prototype.
         * A static method annotated with {@code io.helidon.builder.api.Prototype.FactoryMethod} will be either added here,
         * or to #factoryMethods() depending on signature.
         *
         * @param prototypeFactoryMethods a list of factory methods to add to the prototype
         * @return updated builder instance
         * @see #prototypeFactoryMethods()
         */
        public BUILDER addPrototypeFactoryMethods(List<? extends GeneratedMethod> prototypeFactoryMethods) {
            Objects.requireNonNull(prototypeFactoryMethods);
            this.isPrototypeFactoryMethodsMutated = true;
            this.prototypeFactoryMethods.addAll(prototypeFactoryMethods);
            return self();
        }

        /**
         * Factory methods to be added to the prototype.
         * A static method annotated with {@code io.helidon.builder.api.Prototype.FactoryMethod} will be either added here,
         * or to #factoryMethods() depending on signature.
         *
         * @param prototypeFactoryMethod add single a list of factory methods to add to the prototype
         * @return updated builder instance
         * @see #prototypeFactoryMethods()
         */
        public BUILDER addPrototypeFactoryMethod(GeneratedMethod prototypeFactoryMethod) {
            Objects.requireNonNull(prototypeFactoryMethod);
            this.prototypeFactoryMethods.add(prototypeFactoryMethod);
            this.isPrototypeFactoryMethodsMutated = true;
            return self();
        }

        /**
         * Factory methods to be added to the prototype.
         * A static method annotated with {@code io.helidon.builder.api.Prototype.FactoryMethod} will be either added here,
         * or to #factoryMethods() depending on signature.
         *
         * @param consumer consumer of builder for a list of factory methods to add to the prototype
         * @return updated builder instance
         * @see #prototypeFactoryMethods()
         */
        public BUILDER addPrototypeFactoryMethod(Consumer<GeneratedMethod.Builder> consumer) {
            Objects.requireNonNull(consumer);
            var builder = GeneratedMethod.builder();
            consumer.accept(builder);
            this.addPrototypeFactoryMethod(builder.build());
            return self();
        }

        /**
         * Clear all builderMethods.
         *
         * @return updated builder instance
         * @see #builderMethods()
         */
        public BUILDER clearBuilderMethods() {
            this.isBuilderMethodsMutated = true;
            this.builderMethods.clear();
            return self();
        }

        /**
         * Additional methods to be added to the prototype builder base.
         * It is your responsibility to ensure these methods do not conflict with option methods.
         * This list does NOT contain option methods.
         *
         * @param builderMethods custom methods to add to the prototype builder base
         * @return updated builder instance
         * @see #builderMethods()
         */
        public BUILDER builderMethods(List<? extends GeneratedMethod> builderMethods) {
            Objects.requireNonNull(builderMethods);
            this.isBuilderMethodsMutated = true;
            this.builderMethods.clear();
            this.builderMethods.addAll(builderMethods);
            return self();
        }

        /**
         * Additional methods to be added to the prototype builder base.
         * It is your responsibility to ensure these methods do not conflict with option methods.
         * This list does NOT contain option methods.
         *
         * @param builderMethods custom methods to add to the prototype builder base
         * @return updated builder instance
         * @see #builderMethods()
         */
        public BUILDER addBuilderMethods(List<? extends GeneratedMethod> builderMethods) {
            Objects.requireNonNull(builderMethods);
            this.isBuilderMethodsMutated = true;
            this.builderMethods.addAll(builderMethods);
            return self();
        }

        /**
         * Additional methods to be added to the prototype builder base.
         * It is your responsibility to ensure these methods do not conflict with option methods.
         * This list does NOT contain option methods.
         *
         * @param builderMethod add single custom methods to add to the prototype builder base
         * @return updated builder instance
         * @see #builderMethods()
         */
        public BUILDER addBuilderMethod(GeneratedMethod builderMethod) {
            Objects.requireNonNull(builderMethod);
            this.builderMethods.add(builderMethod);
            this.isBuilderMethodsMutated = true;
            return self();
        }

        /**
         * Additional methods to be added to the prototype builder base.
         * It is your responsibility to ensure these methods do not conflict with option methods.
         * This list does NOT contain option methods.
         *
         * @param consumer consumer of builder for custom methods to add to the prototype builder base
         * @return updated builder instance
         * @see #builderMethods()
         */
        public BUILDER addBuilderMethod(Consumer<GeneratedMethod.Builder> consumer) {
            Objects.requireNonNull(consumer);
            var builder = GeneratedMethod.builder();
            consumer.accept(builder);
            this.addBuilderMethod(builder.build());
            return self();
        }

        /**
         * Clear all factoryMethods.
         *
         * @return updated builder instance
         * @see #factoryMethods()
         */
        public BUILDER clearFactoryMethods() {
            this.isFactoryMethodsMutated = true;
            this.factoryMethods.clear();
            return self();
        }

        /**
         * Factory methods to be used when mapping config to types.
         * These methods will never be made public.
         *
         * @param factoryMethods factory methods to use when mapping config to types
         * @return updated builder instance
         * @see #factoryMethods()
         */
        public BUILDER factoryMethods(List<? extends FactoryMethod> factoryMethods) {
            Objects.requireNonNull(factoryMethods);
            this.isFactoryMethodsMutated = true;
            this.factoryMethods.clear();
            this.factoryMethods.addAll(factoryMethods);
            return self();
        }

        /**
         * Factory methods to be used when mapping config to types.
         * These methods will never be made public.
         *
         * @param factoryMethods factory methods to use when mapping config to types
         * @return updated builder instance
         * @see #factoryMethods()
         */
        public BUILDER addFactoryMethods(List<? extends FactoryMethod> factoryMethods) {
            Objects.requireNonNull(factoryMethods);
            this.isFactoryMethodsMutated = true;
            this.factoryMethods.addAll(factoryMethods);
            return self();
        }

        /**
         * Factory methods to be used when mapping config to types.
         * These methods will never be made public.
         *
         * @param factoryMethod add single factory methods to use when mapping config to types
         * @return updated builder instance
         * @see #factoryMethods()
         */
        public BUILDER addFactoryMethod(FactoryMethod factoryMethod) {
            Objects.requireNonNull(factoryMethod);
            this.factoryMethods.add(factoryMethod);
            this.isFactoryMethodsMutated = true;
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
         * Annotations option. Defined in {@link io.helidon.common.types.Annotated#annotations()}
         *
         * @param annotations the annotations option
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
         * Annotations option. Defined in {@link io.helidon.common.types.Annotated#annotations()}
         *
         * @param annotations the annotations option
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
         * Annotations option. Defined in {@link io.helidon.common.types.Annotated#annotations()}
         *
         * @param annotation add single the annotations option
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
         * Annotations option. Defined in {@link io.helidon.common.types.Annotated#annotations()}
         *
         * @param consumer consumer of builder for the annotations option
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
         * InheritedAnnotations option. Defined in {@link io.helidon.common.types.Annotated#inheritedAnnotations()}
         *
         * @param inheritedAnnotations the inheritedAnnotations option
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
         * InheritedAnnotations option. Defined in {@link io.helidon.common.types.Annotated#inheritedAnnotations()}
         *
         * @param inheritedAnnotations the inheritedAnnotations option
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
         * InheritedAnnotations option. Defined in {@link io.helidon.common.types.Annotated#inheritedAnnotations()}
         *
         * @param inheritedAnnotation add single the inheritedAnnotations option
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
         * InheritedAnnotations option. Defined in {@link io.helidon.common.types.Annotated#inheritedAnnotations()}
         *
         * @param consumer consumer of builder for the inheritedAnnotations option
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
         * Blueprint type info.
         * A new prototype cannot be generated without a blueprint to base it on, so this is a required option.
         *
         * @return blueprint type information
         */
        public Optional<TypeInfo> blueprint() {
            return Optional.ofNullable(blueprint);
        }

        /**
         * If the builder should act as a factory for another type, this is the type.
         * <p>
         * Method {@link io.helidon.builder.api.Prototype.Builder#buildPrototype()} builds the prototype,
         * while method {@link io.helidon.common.Builder#build()} builds the runtime type.
         *
         * @return runtime type, if configured
         */
        public Optional<TypeName> runtimeType() {
            return Optional.ofNullable(runtimeType);
        }

        /**
         * Javadoc for the generated prototype.
         *
         * @return prototype javadoc
         */
        public Optional<Javadoc> javadoc() {
            return Optional.ofNullable(javadoc);
        }

        /**
         * Javadoc for the builder base.
         *
         * @return builder base javadoc
         */
        public Optional<Javadoc> builderBaseJavadoc() {
            return Optional.ofNullable(builderBaseJavadoc);
        }

        /**
         * Javadoc for the builder class.
         *
         * @return builder javadoc
         */
        public Optional<Javadoc> builderJavadoc() {
            return Optional.ofNullable(builderJavadoc);
        }

        /**
         * Builder decorator, if configured.
         *
         * @return type of the builder decorator, if present
         */
        public Optional<TypeName> builderDecorator() {
            return Optional.ofNullable(builderDecorator);
        }

        /**
         * Type name of the generated prototype interface.
         * <p>
         * This interface will contain the following inner classes:
         * <ul>
         *     <li>{@code BuilderBase} - base of the builder with all setters, to support prototype inheritance</li>
         *     <li>{@code Builder} - builder extending the builder base that builds the prototype instance</li>
         *     <li>implementation - prototype implementation class, supports inheritance as well</li>
         * </ul>
         *
         * If you modify the type name to be generated, the result will not support inheritance.
         *
         * @return type of the prototype interface
         */
        public Optional<TypeName> prototypeType() {
            return Optional.ofNullable(prototypeType);
        }

        /**
         * A predicate to include possible interface default methods as options.
         * The default behavior is to exclude all default methods.
         * <p>
         * Sequence of checking if a default method should be an option method:
         * <nl>
         * <li>Check the method signature (i.e. {@code process(java.lang.String)}, if accepted, use it as an option</li>
         * <li>Check the method name (i.e. {@code process}, if accepted, use it as an option</li>
         * <li>Otherwise the default method will not be an option</li>
         * </nl>
         *
         * @return predicate for method names
         */
        public Predicate<String> defaultMethodsPredicate() {
            return defaultMethodsPredicate;
        }

        /**
         * Access modifier for the generated prototype.
         *
         * @return access modifier, defaults to {@code public}
         */
        public AccessModifier accessModifier() {
            return accessModifier;
        }

        /**
         * Access modifier for the generated builder.
         *
         * @return access modifier, defaults to {@code public}
         */
        public AccessModifier builderAccessModifier() {
            return builderAccessModifier;
        }

        /**
         * Whether to create an empty {@code create()} method.
         *
         * @return whether to create an empty {@code create()} method, defaults to {@code true}
         */
        public boolean createEmptyCreate() {
            return createEmptyCreate;
        }

        /**
         * Whether to use record style or bean style accessors.
         * <p>
         * Let's consider option {@code accessModifier} of type {@code AccessModifier}.
         * <p>
         * Record style:
         * <ul>
         *     <li>Getter: {@code AccessModifier accessModifier()}</li>
         *     <li>Setter: {@code Builder accessModifier(AccessModifier)}</li>
         * </ul>
         * Bean style:
         * <ul>
         *     <li>Getter: {@code AccessModifier getAccessModifier()}</li>
         *     <li>Setter: {@code Builder setAccessModifier(AccessModifier)}</li>
         * </ul>
         *
         * @return whether to use record style accessors, defaults to {@code true}
         */
        public boolean recordStyle() {
            return recordStyle;
        }

        /**
         * Prototype configuration details.
         *
         * @return prototype configuration details, if configured
         */
        public Optional<PrototypeConfigured> configured() {
            return Optional.ofNullable(configured);
        }

        /**
         * Whether to use the service registry to discover providers.
         *
         * @return whether to support service registry, defaults to {@code false}
         */
        public boolean registrySupport() {
            return registrySupport;
        }

        /**
         * Whether to detach the blueprint from the generated prototype.
         *
         * @return true if the blueprint should not be extended by the prototype
         */
        public boolean detachBlueprint() {
            return detachBlueprint;
        }

        /**
         * If the blueprint extends an existing prototype (or blueprint), we must extend that prototype and
         * also that prototype's builder.
         *
         * @return super prototype, if present
         */
        public Optional<TypeName> superPrototype() {
            return Optional.ofNullable(superPrototype);
        }

        /**
         * List of types the prototype should extend.
         * This list will always contain the blueprint interface, and {@link io.helidon.builder.api.Prototype.Api}.
         * This list also contains {@link #superPrototype()} if present.
         *
         * @return types the prototype must extend
         */
        public Set<TypeName> superTypes() {
            return superTypes;
        }

        /**
         * Types the generated prototype should provide, if this prototype is/configures a service provider.
         *
         * @return provider provides types
         */
        public Set<TypeName> providerProvides() {
            return providerProvides;
        }

        /**
         * Constants to be defined on the prototype.
         * A constant may be either a reference to another constant or a generated value.
         *
         * @return constants to add to the prototype
         */
        public List<PrototypeConstant> constants() {
            return constants;
        }

        /**
         * Additional methods to be added to the prototype as default methods.
         * <p>
         * Non-default interface methods cannot be added, as the implementation is not customizable.
         * This list does NOT contain option methods.
         *
         * @return custom methods to add to the prototype
         */
        public List<GeneratedMethod> prototypeMethods() {
            return prototypeMethods;
        }

        /**
         * Factory methods to be added to the prototype.
         * A static method annotated with {@code io.helidon.builder.api.Prototype.FactoryMethod} will be either added here,
         * or to #factoryMethods() depending on signature.
         *
         * @return a list of factory methods to add to the prototype
         */
        public List<GeneratedMethod> prototypeFactoryMethods() {
            return prototypeFactoryMethods;
        }

        /**
         * Additional methods to be added to the prototype builder base.
         * It is your responsibility to ensure these methods do not conflict with option methods.
         * This list does NOT contain option methods.
         *
         * @return custom methods to add to the prototype builder base
         */
        public List<GeneratedMethod> builderMethods() {
            return builderMethods;
        }

        /**
         * Factory methods to be used when mapping config to types.
         * These methods will never be made public.
         *
         * @return factory methods to use when mapping config to types
         */
        public List<FactoryMethod> factoryMethods() {
            return factoryMethods;
        }

        /**
         * Annotations option. Defined in {@link io.helidon.common.types.Annotated#annotations()}
         *
         * @return the annotations option
         */
        public List<Annotation> annotations() {
            return annotations;
        }

        /**
         * InheritedAnnotations option. Defined in {@link io.helidon.common.types.Annotated#inheritedAnnotations()}
         *
         * @return the inheritedAnnotations option
         */
        public List<Annotation> inheritedAnnotations() {
            return inheritedAnnotations;
        }

        @Override
        public String toString() {
            return "PrototypeInfoBuilder{"
                    + "blueprint=" + blueprint + ","
                    + "runtimeType=" + runtimeType + ","
                    + "javadoc=" + javadoc + ","
                    + "builderBaseJavadoc=" + builderBaseJavadoc + ","
                    + "builderJavadoc=" + builderJavadoc + ","
                    + "builderDecorator=" + builderDecorator + ","
                    + "prototypeType=" + prototypeType + ","
                    + "defaultMethodsPredicate=" + defaultMethodsPredicate + ","
                    + "accessModifier=" + accessModifier + ","
                    + "builderAccessModifier=" + builderAccessModifier + ","
                    + "createEmptyCreate=" + createEmptyCreate + ","
                    + "recordStyle=" + recordStyle + ","
                    + "configured=" + configured + ","
                    + "registrySupport=" + registrySupport + ","
                    + "detachBlueprint=" + detachBlueprint + ","
                    + "superPrototype=" + superPrototype + ","
                    + "superTypes=" + superTypes + ","
                    + "providerProvides=" + providerProvides + ","
                    + "constants=" + constants + ","
                    + "prototypeMethods=" + prototypeMethods + ","
                    + "prototypeFactoryMethods=" + prototypeFactoryMethods + ","
                    + "builderMethods=" + builderMethods + ","
                    + "factoryMethods=" + factoryMethods + ","
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
            if (blueprint == null) {
                collector.fatal(getClass(), "Property \"blueprint\" must not be null, but not set");
            }
            if (javadoc == null) {
                collector.fatal(getClass(), "Property \"javadoc\" must not be null, but not set");
            }
            if (builderBaseJavadoc == null) {
                collector.fatal(getClass(), "Property \"builderBaseJavadoc\" must not be null, but not set");
            }
            if (builderJavadoc == null) {
                collector.fatal(getClass(), "Property \"builderJavadoc\" must not be null, but not set");
            }
            if (prototypeType == null) {
                collector.fatal(getClass(), "Property \"prototypeType\" must not be null, but not set");
            }
            collector.collect().checkValid();
        }

        /**
         * If the builder should act as a factory for another type, this is the type.
         * <p>
         * Method {@link io.helidon.builder.api.Prototype.Builder#buildPrototype()} builds the prototype,
         * while method {@link io.helidon.common.Builder#build()} builds the runtime type.
         *
         * @param runtimeType runtime type, if configured
         * @return updated builder instance
         * @see #runtimeType()
         */
        @SuppressWarnings("unchecked")
        BUILDER runtimeType(Optional<? extends TypeName> runtimeType) {
            Objects.requireNonNull(runtimeType);
            this.runtimeType = runtimeType.map(TypeName.class::cast).orElse(this.runtimeType);
            return self();
        }

        /**
         * Builder decorator, if configured.
         *
         * @param builderDecorator type of the builder decorator, if present
         * @return updated builder instance
         * @see #builderDecorator()
         */
        @SuppressWarnings("unchecked")
        BUILDER builderDecorator(Optional<? extends TypeName> builderDecorator) {
            Objects.requireNonNull(builderDecorator);
            this.builderDecorator = builderDecorator.map(TypeName.class::cast).orElse(this.builderDecorator);
            return self();
        }

        /**
         * Prototype configuration details.
         *
         * @param configured prototype configuration details, if configured
         * @return updated builder instance
         * @see #configured()
         */
        @SuppressWarnings("unchecked")
        BUILDER configured(Optional<? extends PrototypeConfigured> configured) {
            Objects.requireNonNull(configured);
            this.configured = configured.map(PrototypeConfigured.class::cast).orElse(this.configured);
            return self();
        }

        /**
         * If the blueprint extends an existing prototype (or blueprint), we must extend that prototype and
         * also that prototype's builder.
         *
         * @param superPrototype super prototype, if present
         * @return updated builder instance
         * @see #superPrototype()
         */
        @SuppressWarnings("unchecked")
        BUILDER superPrototype(Optional<? extends TypeName> superPrototype) {
            Objects.requireNonNull(superPrototype);
            this.superPrototype = superPrototype.map(TypeName.class::cast).orElse(this.superPrototype);
            return self();
        }

        /**
         * Generated implementation of the prototype, can be extended by descendant prototype implementations.
         */
        protected static class PrototypeInfoImpl implements PrototypeInfo {

            private final AccessModifier accessModifier;
            private final AccessModifier builderAccessModifier;
            private final boolean createEmptyCreate;
            private final boolean detachBlueprint;
            private final boolean recordStyle;
            private final boolean registrySupport;
            private final Javadoc builderBaseJavadoc;
            private final Javadoc builderJavadoc;
            private final Javadoc javadoc;
            private final List<GeneratedMethod> builderMethods;
            private final List<GeneratedMethod> prototypeFactoryMethods;
            private final List<GeneratedMethod> prototypeMethods;
            private final List<PrototypeConstant> constants;
            private final List<FactoryMethod> factoryMethods;
            private final List<Annotation> annotations;
            private final List<Annotation> inheritedAnnotations;
            private final Optional<PrototypeConfigured> configured;
            private final Optional<TypeName> builderDecorator;
            private final Optional<TypeName> runtimeType;
            private final Optional<TypeName> superPrototype;
            private final Predicate<String> defaultMethodsPredicate;
            private final Set<TypeName> providerProvides;
            private final Set<TypeName> superTypes;
            private final TypeInfo blueprint;
            private final TypeName prototypeType;

            /**
             * Create an instance providing a builder.
             *
             * @param builder extending builder base of this prototype
             */
            protected PrototypeInfoImpl(BuilderBase<?, ?> builder) {
                this.blueprint = builder.blueprint().get();
                this.runtimeType = builder.runtimeType().map(Function.identity());
                this.javadoc = builder.javadoc().get();
                this.builderBaseJavadoc = builder.builderBaseJavadoc().get();
                this.builderJavadoc = builder.builderJavadoc().get();
                this.builderDecorator = builder.builderDecorator().map(Function.identity());
                this.prototypeType = builder.prototypeType().get();
                this.defaultMethodsPredicate = builder.defaultMethodsPredicate();
                this.accessModifier = builder.accessModifier();
                this.builderAccessModifier = builder.builderAccessModifier();
                this.createEmptyCreate = builder.createEmptyCreate();
                this.recordStyle = builder.recordStyle();
                this.configured = builder.configured().map(Function.identity());
                this.registrySupport = builder.registrySupport();
                this.detachBlueprint = builder.detachBlueprint();
                this.superPrototype = builder.superPrototype().map(Function.identity());
                this.superTypes = Collections.unmodifiableSet(new LinkedHashSet<>(builder.superTypes()));
                this.providerProvides = Collections.unmodifiableSet(new LinkedHashSet<>(builder.providerProvides()));
                this.constants = List.copyOf(builder.constants());
                this.prototypeMethods = List.copyOf(builder.prototypeMethods());
                this.prototypeFactoryMethods = List.copyOf(builder.prototypeFactoryMethods());
                this.builderMethods = List.copyOf(builder.builderMethods());
                this.factoryMethods = List.copyOf(builder.factoryMethods());
                this.annotations = List.copyOf(builder.annotations());
                this.inheritedAnnotations = List.copyOf(builder.inheritedAnnotations());
            }

            @Override
            public TypeInfo blueprint() {
                return blueprint;
            }

            @Override
            public Optional<TypeName> runtimeType() {
                return runtimeType;
            }

            @Override
            public Javadoc javadoc() {
                return javadoc;
            }

            @Override
            public Javadoc builderBaseJavadoc() {
                return builderBaseJavadoc;
            }

            @Override
            public Javadoc builderJavadoc() {
                return builderJavadoc;
            }

            @Override
            public Optional<TypeName> builderDecorator() {
                return builderDecorator;
            }

            @Override
            public TypeName prototypeType() {
                return prototypeType;
            }

            @Override
            public Predicate<String> defaultMethodsPredicate() {
                return defaultMethodsPredicate;
            }

            @Override
            public AccessModifier accessModifier() {
                return accessModifier;
            }

            @Override
            public AccessModifier builderAccessModifier() {
                return builderAccessModifier;
            }

            @Override
            public boolean createEmptyCreate() {
                return createEmptyCreate;
            }

            @Override
            public boolean recordStyle() {
                return recordStyle;
            }

            @Override
            public Optional<PrototypeConfigured> configured() {
                return configured;
            }

            @Override
            public boolean registrySupport() {
                return registrySupport;
            }

            @Override
            public boolean detachBlueprint() {
                return detachBlueprint;
            }

            @Override
            public Optional<TypeName> superPrototype() {
                return superPrototype;
            }

            @Override
            public Set<TypeName> superTypes() {
                return superTypes;
            }

            @Override
            public Set<TypeName> providerProvides() {
                return providerProvides;
            }

            @Override
            public List<PrototypeConstant> constants() {
                return constants;
            }

            @Override
            public List<GeneratedMethod> prototypeMethods() {
                return prototypeMethods;
            }

            @Override
            public List<GeneratedMethod> prototypeFactoryMethods() {
                return prototypeFactoryMethods;
            }

            @Override
            public List<GeneratedMethod> builderMethods() {
                return builderMethods;
            }

            @Override
            public List<FactoryMethod> factoryMethods() {
                return factoryMethods;
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
                return "PrototypeInfo{"
                        + "blueprint=" + blueprint + ","
                        + "runtimeType=" + runtimeType + ","
                        + "javadoc=" + javadoc + ","
                        + "builderBaseJavadoc=" + builderBaseJavadoc + ","
                        + "builderJavadoc=" + builderJavadoc + ","
                        + "builderDecorator=" + builderDecorator + ","
                        + "prototypeType=" + prototypeType + ","
                        + "defaultMethodsPredicate=" + defaultMethodsPredicate + ","
                        + "accessModifier=" + accessModifier + ","
                        + "builderAccessModifier=" + builderAccessModifier + ","
                        + "createEmptyCreate=" + createEmptyCreate + ","
                        + "recordStyle=" + recordStyle + ","
                        + "configured=" + configured + ","
                        + "registrySupport=" + registrySupport + ","
                        + "detachBlueprint=" + detachBlueprint + ","
                        + "superPrototype=" + superPrototype + ","
                        + "superTypes=" + superTypes + ","
                        + "providerProvides=" + providerProvides + ","
                        + "constants=" + constants + ","
                        + "prototypeMethods=" + prototypeMethods + ","
                        + "prototypeFactoryMethods=" + prototypeFactoryMethods + ","
                        + "builderMethods=" + builderMethods + ","
                        + "factoryMethods=" + factoryMethods + ","
                        + "annotations=" + annotations + ","
                        + "inheritedAnnotations=" + inheritedAnnotations
                        + "}";
            }

            @Override
            public boolean equals(Object o) {
                if (o == this) {
                    return true;
                }
                if (!(o instanceof PrototypeInfo other)) {
                    return false;
                }
                return Objects.equals(blueprint, other.blueprint())
                        && Objects.equals(runtimeType, other.runtimeType())
                        && Objects.equals(javadoc, other.javadoc())
                        && Objects.equals(builderBaseJavadoc, other.builderBaseJavadoc())
                        && Objects.equals(builderJavadoc, other.builderJavadoc())
                        && Objects.equals(builderDecorator, other.builderDecorator())
                        && Objects.equals(prototypeType, other.prototypeType())
                        && Objects.equals(defaultMethodsPredicate, other.defaultMethodsPredicate())
                        && Objects.equals(accessModifier, other.accessModifier())
                        && Objects.equals(builderAccessModifier, other.builderAccessModifier())
                        && createEmptyCreate == other.createEmptyCreate()
                        && recordStyle == other.recordStyle()
                        && Objects.equals(configured, other.configured())
                        && registrySupport == other.registrySupport()
                        && detachBlueprint == other.detachBlueprint()
                        && Objects.equals(superPrototype, other.superPrototype())
                        && Objects.equals(superTypes, other.superTypes())
                        && Objects.equals(providerProvides, other.providerProvides())
                        && Objects.equals(constants, other.constants())
                        && Objects.equals(prototypeMethods, other.prototypeMethods())
                        && Objects.equals(prototypeFactoryMethods, other.prototypeFactoryMethods())
                        && Objects.equals(builderMethods, other.builderMethods())
                        && Objects.equals(factoryMethods, other.factoryMethods())
                        && Objects.equals(annotations, other.annotations())
                        && Objects.equals(inheritedAnnotations, other.inheritedAnnotations());
            }

            @Override
            public int hashCode() {
                return Objects.hash(blueprint,
                                    runtimeType,
                                    javadoc,
                                    builderBaseJavadoc,
                                    builderJavadoc,
                                    builderDecorator,
                                    prototypeType,
                                    defaultMethodsPredicate,
                                    accessModifier,
                                    builderAccessModifier,
                                    createEmptyCreate,
                                    recordStyle,
                                    configured,
                                    registrySupport,
                                    detachBlueprint,
                                    superPrototype,
                                    superTypes,
                                    providerProvides,
                                    constants,
                                    prototypeMethods,
                                    prototypeFactoryMethods,
                                    builderMethods,
                                    factoryMethods,
                                    annotations,
                                    inheritedAnnotations);
            }

        }

    }

    /**
     * Fluent API builder for {@link io.helidon.builder.codegen.PrototypeInfo}.
     */
    class Builder extends BuilderBase<Builder, PrototypeInfo> implements io.helidon.common.Builder<Builder, PrototypeInfo> {

        private Builder() {
        }

        @Override
        public PrototypeInfo buildPrototype() {
            preBuildPrototype();
            validatePrototype();
            return new PrototypeInfoImpl(this);
        }

        @Override
        public PrototypeInfo build() {
            return buildPrototype();
        }

    }

}
