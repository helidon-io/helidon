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
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import io.helidon.builder.api.Prototype;
import io.helidon.codegen.classmodel.ContentBuilder;
import io.helidon.common.Errors;
import io.helidon.common.Generated;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypedElementInfo;

/**
 * Model of a prototype/builder option.
 *
 * @see #builder()
 */
@Generated(value = "io.helidon.builder.codegen.BuilderCodegen", trigger = "io.helidon.builder.codegen.OptionInfoBlueprint")
public interface OptionInfo extends Prototype.Api {

    /**
     * Create a new fluent API builder to customize configuration.
     *
     * @return a new builder
     */
    static OptionInfo.Builder builder() {
        return new OptionInfo.Builder();
    }

    /**
     * Create a new fluent API builder from an existing instance.
     *
     * @param instance an existing instance used as a base for the builder
     * @return a builder based on an instance
     */
    static OptionInfo.Builder builder(OptionInfo instance) {
        return OptionInfo.builder().from(instance);
    }

    /**
     * Blueprint method if created from blueprint.
     * This may be also a method on a non-blueprint interface, in case the blueprint extends from it.
     * This must be filled in to generate the {@link java.lang.Override} annotation on the generated method.
     *
     * @return blueprint method, if present
     */
    Optional<TypedElementInfo> blueprintMethod();

    /**
     * Prototype getter definition.
     * This method is always abstract (interface method).
     *
     * @return prototype getter
     */
    TypedElementInfo getter();

    /**
     * Builder getter definition.
     * For non-collection types, this always returns an {@link java.util.Optional}, unless there is a default value
     * defined for a non-optional option.
     *
     * @return builder getter
     */
    TypedElementInfo builderGetter();

    /**
     * Builder setter definition.
     * This is a setter for option's declared type, unless the type is {@link java.util.Optional}.
     * For optional options, the setter will have a non-optional parameter, and an unset method is generated as well.
     *
     * @return builder setter
     */
    TypedElementInfo setter();

    /**
     * If an option method returns {@link java.util.Optional}, a setter will be created
     * without the optional parameter (as {@link #setter()}, and another one with an optional parameter for
     * copy methods.
     *
     * @return setter with optional parameter, if present
     */
    Optional<TypedElementInfo> setterForOptional();

    /**
     * Getter that is generated on the implementation class.
     * As the implementation class implements the prototype, this method is always annotated
     * with {@link java.lang.Override}.
     *
     * @return implementation getter
     */
    TypedElementInfo implGetter();

    /**
     * Option name.
     *
     * @return name of this option
     */
    String name();

    /**
     * The return type of the blueprint method, or the type expected in getter of the option.
     *
     * @return declared type
     */
    TypeName declaredType();

    /**
     * Option decorator type.
     *
     * @return type of the option decorator, if present
     */
    Optional<TypeName> decorator();

    /**
     * Whether to include this option in generated {@link java.lang.Object#toString()} method
     *
     * @return whether to include in the {@code toString} method
     */
    boolean includeInToString();

    /**
     * Whether to include this option in generated {@link java.lang.Object#equals(Object)}
     * and {@link java.lang.Object#hashCode()} methods.
     *
     * @return whether to include in the {@code equals} and {@code hashCode} methods
     */
    boolean includeInEqualsAndHashCode();

    /**
     * Whether this option is confidential (i.e. we should not print the value in {@code toString} method).
     *
     * @return whether this option is confidential
     */
    boolean confidential();

    /**
     * Whether this option should be loaded from {@code ServiceRegistry}.
     *
     * @return whether this option should be loaded from {@code ServiceRegistry}
     */
    boolean registryService();

    /**
     * Whether this {@link java.util.Map} option is expected to have the same generic type for key and value.
     * For example, for a {@code Map<Class<?>, Instance<?>>} that has {@code sameGeneric} set to {@code true}, we would
     * generate singular method with signature {@code <T> put(Class<T>, Instance<T>}.
     *
     * @return whether a map has the same generic in key and value for a single mapping
     */
    boolean sameGeneric();

    /**
     * Will be true if:
     * <ul>
     *     <li>an {@code Option.Required} is present in blueprint</li>
     *     <li>the return type on blueprint is not {@link java.util.Optional} or a collection, and we do not support null</li>
     * </ul>
     *
     * @return whether the option is required
     */
    boolean required();

    /**
     * List of qualifiers for this option.
     *
     * @return service registry qualifiers defined on this option (to be used when getting a service registry instance)
     */
    List<Annotation> qualifiers();

    /**
     * List of allowed values for this option.
     *
     * @return allowed values
     */
    List<OptionAllowedValue> allowedValues();

    /**
     * Default value for this option, a consumer of the field content builder.
     *
     * @return default value consumer
     */
    Optional<Consumer<ContentBuilder<?>>> defaultValue();

    /**
     * Details about configurability of this option.
     *
     * @return configured setup if configured
     */
    Optional<OptionConfigured> configured();

    /**
     * Deprecation details.
     *
     * @return deprecation details, if present
     */
    Optional<OptionDeprecation> deprecation();

    /**
     * Provider details.
     *
     * @return provider details, if present
     */
    Optional<OptionProvider> provider();

    /**
     * Singular option details.
     *
     * @return singular setter name and related information, if present
     */
    Optional<OptionSingular> singular();

    /**
     * Access modifier of generated setter methods on builder.
     * Note that prototype methods are declared in an interface, so these must always be public.
     *
     * @return access modifier to use
     */
    AccessModifier accessModifier();

    /**
     * If the option has a builder, return its information.
     *
     * @return builder information, if present
     */
    Optional<OptionBuilder> builderInfo();

    /**
     *
     */
    List<Annotation> annotations();

    /**
     *
     */
    List<Annotation> inheritedAnnotations();

    /**
     * Fluent API builder base for {@link OptionInfo}.
     *
     * @param <BUILDER>   type of the builder extending this abstract builder
     * @param <PROTOTYPE> type of the prototype interface that would be built by {@link #buildPrototype()}
     */
    abstract class BuilderBase<BUILDER extends OptionInfo.BuilderBase<BUILDER, PROTOTYPE>, PROTOTYPE extends OptionInfo>
            implements Prototype.Builder<BUILDER, PROTOTYPE> {

        private final List<OptionAllowedValue> allowedValues = new ArrayList<>();
        private final List<Annotation> annotations = new ArrayList<>();
        private final List<Annotation> inheritedAnnotations = new ArrayList<>();
        private final List<Annotation> qualifiers = new ArrayList<>();
        private AccessModifier accessModifier = AccessModifier.PUBLIC;
        private boolean confidential = false;
        private boolean includeInEqualsAndHashCode = true;
        private boolean includeInToString = true;
        private boolean isAllowedValuesMutated;
        private boolean isAnnotationsMutated;
        private boolean isInheritedAnnotationsMutated;
        private boolean isQualifiersMutated;
        private boolean registryService = false;
        private boolean required;
        private boolean sameGeneric = false;
        private Consumer<ContentBuilder<?>> defaultValue;
        private OptionBuilder builderInfo;
        private OptionConfigured configured;
        private OptionDeprecation deprecation;
        private OptionProvider provider;
        private OptionSingular singular;
        private String name;
        private TypedElementInfo blueprintMethod;
        private TypedElementInfo builderGetter;
        private TypedElementInfo getter;
        private TypedElementInfo implGetter;
        private TypedElementInfo setter;
        private TypedElementInfo setterForOptional;
        private TypeName declaredType;
        private TypeName decorator;

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
        public BUILDER from(OptionInfo prototype) {
            blueprintMethod(prototype.blueprintMethod());
            getter(prototype.getter());
            builderGetter(prototype.builderGetter());
            setter(prototype.setter());
            setterForOptional(prototype.setterForOptional());
            implGetter(prototype.implGetter());
            name(prototype.name());
            declaredType(prototype.declaredType());
            decorator(prototype.decorator());
            includeInToString(prototype.includeInToString());
            includeInEqualsAndHashCode(prototype.includeInEqualsAndHashCode());
            confidential(prototype.confidential());
            registryService(prototype.registryService());
            sameGeneric(prototype.sameGeneric());
            required(prototype.required());
            if (!isQualifiersMutated) {
                qualifiers.clear();
            }
            addQualifiers(prototype.qualifiers());
            if (!isAllowedValuesMutated) {
                allowedValues.clear();
            }
            addAllowedValues(prototype.allowedValues());
            defaultValue(prototype.defaultValue());
            configured(prototype.configured());
            deprecation(prototype.deprecation());
            provider(prototype.provider());
            singular(prototype.singular());
            accessModifier(prototype.accessModifier());
            builderInfo(prototype.builderInfo());
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
        public BUILDER from(OptionInfo.BuilderBase<?, ?> builder) {
            builder.blueprintMethod().ifPresent(this::blueprintMethod);
            builder.getter().ifPresent(this::getter);
            builder.builderGetter().ifPresent(this::builderGetter);
            builder.setter().ifPresent(this::setter);
            builder.setterForOptional().ifPresent(this::setterForOptional);
            builder.implGetter().ifPresent(this::implGetter);
            builder.name().ifPresent(this::name);
            builder.declaredType().ifPresent(this::declaredType);
            builder.decorator().ifPresent(this::decorator);
            includeInToString(builder.includeInToString());
            includeInEqualsAndHashCode(builder.includeInEqualsAndHashCode());
            confidential(builder.confidential());
            registryService(builder.registryService());
            sameGeneric(builder.sameGeneric());
            required(builder.required());
            if (isQualifiersMutated) {
                if (builder.isQualifiersMutated) {
                    addQualifiers(builder.qualifiers);
                }
            } else {
                qualifiers.clear();
                addQualifiers(builder.qualifiers);
            }
            if (isAllowedValuesMutated) {
                if (builder.isAllowedValuesMutated) {
                    addAllowedValues(builder.allowedValues);
                }
            } else {
                allowedValues.clear();
                addAllowedValues(builder.allowedValues);
            }
            builder.defaultValue().ifPresent(this::defaultValue);
            builder.configured().ifPresent(this::configured);
            builder.deprecation().ifPresent(this::deprecation);
            builder.provider().ifPresent(this::provider);
            builder.singular().ifPresent(this::singular);
            accessModifier(builder.accessModifier());
            builder.builderInfo().ifPresent(this::builderInfo);
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
         * Clear existing value of this property.
         *
         * @return updated builder instance
         * @see #blueprintMethod()
         */
        public BUILDER clearBlueprintMethod() {
            this.blueprintMethod = null;
            return self();
        }

        /**
         * Blueprint method if created from blueprint.
         * This may be also a method on a non-blueprint interface, in case the blueprint extends from it.
         * This must be filled in to generate the {@link java.lang.Override} annotation on the generated method.
         *
         * @param blueprintMethod blueprint method, if present
         * @return updated builder instance
         * @see #blueprintMethod()
         */
        public BUILDER blueprintMethod(TypedElementInfo blueprintMethod) {
            Objects.requireNonNull(blueprintMethod);
            this.blueprintMethod = blueprintMethod;
            return self();
        }

        /**
         * Blueprint method if created from blueprint.
         * This may be also a method on a non-blueprint interface, in case the blueprint extends from it.
         * This must be filled in to generate the {@link java.lang.Override} annotation on the generated method.
         *
         * @param consumer consumer of builder
         * @return updated builder instance
         * @see #blueprintMethod()
         */
        public BUILDER blueprintMethod(Consumer<TypedElementInfo.Builder> consumer) {
            Objects.requireNonNull(consumer);
            var builder = TypedElementInfo.builder();
            consumer.accept(builder);
            this.blueprintMethod(builder.build());
            return self();
        }

        /**
         * Prototype getter definition.
         * This method is always abstract (interface method).
         *
         * @param getter prototype getter
         * @return updated builder instance
         * @see #getter()
         */
        public BUILDER getter(TypedElementInfo getter) {
            Objects.requireNonNull(getter);
            this.getter = getter;
            return self();
        }

        /**
         * Prototype getter definition.
         * This method is always abstract (interface method).
         *
         * @param consumer consumer of builder
         * @return updated builder instance
         * @see #getter()
         */
        public BUILDER getter(Consumer<TypedElementInfo.Builder> consumer) {
            Objects.requireNonNull(consumer);
            var builder = TypedElementInfo.builder();
            consumer.accept(builder);
            this.getter(builder.build());
            return self();
        }

        /**
         * Prototype getter definition.
         * This method is always abstract (interface method).
         *
         * @param supplier supplier of value, such as a {@link io.helidon.common.Builder}
         * @return updated builder instance
         * @see #getter()
         */
        public BUILDER getter(Supplier<? extends TypedElementInfo> supplier) {
            Objects.requireNonNull(supplier);
            this.getter(supplier.get());
            return self();
        }

        /**
         * Builder getter definition.
         * For non-collection types, this always returns an {@link java.util.Optional}, unless there is a default value
         * defined for a non-optional option.
         *
         * @param builderGetter builder getter
         * @return updated builder instance
         * @see #builderGetter()
         */
        public BUILDER builderGetter(TypedElementInfo builderGetter) {
            Objects.requireNonNull(builderGetter);
            this.builderGetter = builderGetter;
            return self();
        }

        /**
         * Builder getter definition.
         * For non-collection types, this always returns an {@link java.util.Optional}, unless there is a default value
         * defined for a non-optional option.
         *
         * @param consumer consumer of builder
         * @return updated builder instance
         * @see #builderGetter()
         */
        public BUILDER builderGetter(Consumer<TypedElementInfo.Builder> consumer) {
            Objects.requireNonNull(consumer);
            var builder = TypedElementInfo.builder();
            consumer.accept(builder);
            this.builderGetter(builder.build());
            return self();
        }

        /**
         * Builder getter definition.
         * For non-collection types, this always returns an {@link java.util.Optional}, unless there is a default value
         * defined for a non-optional option.
         *
         * @param supplier supplier of value, such as a {@link io.helidon.common.Builder}
         * @return updated builder instance
         * @see #builderGetter()
         */
        public BUILDER builderGetter(Supplier<? extends TypedElementInfo> supplier) {
            Objects.requireNonNull(supplier);
            this.builderGetter(supplier.get());
            return self();
        }

        /**
         * Builder setter definition.
         * This is a setter for option's declared type, unless the type is {@link java.util.Optional}.
         * For optional options, the setter will have a non-optional parameter, and an unset method is generated as well.
         *
         * @param setter builder setter
         * @return updated builder instance
         * @see #setter()
         */
        public BUILDER setter(TypedElementInfo setter) {
            Objects.requireNonNull(setter);
            this.setter = setter;
            return self();
        }

        /**
         * Builder setter definition.
         * This is a setter for option's declared type, unless the type is {@link java.util.Optional}.
         * For optional options, the setter will have a non-optional parameter, and an unset method is generated as well.
         *
         * @param consumer consumer of builder
         * @return updated builder instance
         * @see #setter()
         */
        public BUILDER setter(Consumer<TypedElementInfo.Builder> consumer) {
            Objects.requireNonNull(consumer);
            var builder = TypedElementInfo.builder();
            consumer.accept(builder);
            this.setter(builder.build());
            return self();
        }

        /**
         * Builder setter definition.
         * This is a setter for option's declared type, unless the type is {@link java.util.Optional}.
         * For optional options, the setter will have a non-optional parameter, and an unset method is generated as well.
         *
         * @param supplier supplier of value, such as a {@link io.helidon.common.Builder}
         * @return updated builder instance
         * @see #setter()
         */
        public BUILDER setter(Supplier<? extends TypedElementInfo> supplier) {
            Objects.requireNonNull(supplier);
            this.setter(supplier.get());
            return self();
        }

        /**
         * Clear existing value of this property.
         *
         * @return updated builder instance
         * @see #setterForOptional()
         */
        public BUILDER clearSetterForOptional() {
            this.setterForOptional = null;
            return self();
        }

        /**
         * If an option method returns {@link java.util.Optional}, a setter will be created
         * without the optional parameter (as {@link #setter()}, and another one with an optional parameter for
         * copy methods.
         *
         * @param setterForOptional setter with optional parameter, if present
         * @return updated builder instance
         * @see #setterForOptional()
         */
        public BUILDER setterForOptional(TypedElementInfo setterForOptional) {
            Objects.requireNonNull(setterForOptional);
            this.setterForOptional = setterForOptional;
            return self();
        }

        /**
         * If an option method returns {@link java.util.Optional}, a setter will be created
         * without the optional parameter (as {@link #setter()}, and another one with an optional parameter for
         * copy methods.
         *
         * @param consumer consumer of builder
         * @return updated builder instance
         * @see #setterForOptional()
         */
        public BUILDER setterForOptional(Consumer<TypedElementInfo.Builder> consumer) {
            Objects.requireNonNull(consumer);
            var builder = TypedElementInfo.builder();
            consumer.accept(builder);
            this.setterForOptional(builder.build());
            return self();
        }

        /**
         * Getter that is generated on the implementation class.
         * As the implementation class implements the prototype, this method is always annotated
         * with {@link java.lang.Override}.
         *
         * @param implGetter implementation getter
         * @return updated builder instance
         * @see #implGetter()
         */
        public BUILDER implGetter(TypedElementInfo implGetter) {
            Objects.requireNonNull(implGetter);
            this.implGetter = implGetter;
            return self();
        }

        /**
         * Getter that is generated on the implementation class.
         * As the implementation class implements the prototype, this method is always annotated
         * with {@link java.lang.Override}.
         *
         * @param consumer consumer of builder
         * @return updated builder instance
         * @see #implGetter()
         */
        public BUILDER implGetter(Consumer<TypedElementInfo.Builder> consumer) {
            Objects.requireNonNull(consumer);
            var builder = TypedElementInfo.builder();
            consumer.accept(builder);
            this.implGetter(builder.build());
            return self();
        }

        /**
         * Getter that is generated on the implementation class.
         * As the implementation class implements the prototype, this method is always annotated
         * with {@link java.lang.Override}.
         *
         * @param supplier supplier of value, such as a {@link io.helidon.common.Builder}
         * @return updated builder instance
         * @see #implGetter()
         */
        public BUILDER implGetter(Supplier<? extends TypedElementInfo> supplier) {
            Objects.requireNonNull(supplier);
            this.implGetter(supplier.get());
            return self();
        }

        /**
         * Option name.
         *
         * @param name name of this option
         * @return updated builder instance
         * @see #name()
         */
        public BUILDER name(String name) {
            Objects.requireNonNull(name);
            this.name = name;
            return self();
        }

        /**
         * The return type of the blueprint method, or the type expected in getter of the option.
         *
         * @param declaredType declared type
         * @return updated builder instance
         * @see #declaredType()
         */
        public BUILDER declaredType(TypeName declaredType) {
            Objects.requireNonNull(declaredType);
            this.declaredType = declaredType;
            return self();
        }

        /**
         * The return type of the blueprint method, or the type expected in getter of the option.
         *
         * @param consumer consumer of builder
         * @return updated builder instance
         * @see #declaredType()
         */
        public BUILDER declaredType(Consumer<TypeName.Builder> consumer) {
            Objects.requireNonNull(consumer);
            var builder = TypeName.builder();
            consumer.accept(builder);
            this.declaredType(builder.build());
            return self();
        }

        /**
         * The return type of the blueprint method, or the type expected in getter of the option.
         *
         * @param supplier supplier of value, such as a {@link io.helidon.common.Builder}
         * @return updated builder instance
         * @see #declaredType()
         */
        public BUILDER declaredType(Supplier<? extends TypeName> supplier) {
            Objects.requireNonNull(supplier);
            this.declaredType(supplier.get());
            return self();
        }

        /**
         * Clear existing value of this property.
         *
         * @return updated builder instance
         * @see #decorator()
         */
        public BUILDER clearDecorator() {
            this.decorator = null;
            return self();
        }

        /**
         * Option decorator type.
         *
         * @param decorator type of the option decorator, if present
         * @return updated builder instance
         * @see #decorator()
         */
        public BUILDER decorator(TypeName decorator) {
            Objects.requireNonNull(decorator);
            this.decorator = decorator;
            return self();
        }

        /**
         * Option decorator type.
         *
         * @param consumer consumer of builder
         * @return updated builder instance
         * @see #decorator()
         */
        public BUILDER decorator(Consumer<TypeName.Builder> consumer) {
            Objects.requireNonNull(consumer);
            var builder = TypeName.builder();
            consumer.accept(builder);
            this.decorator(builder.build());
            return self();
        }

        /**
         * Whether to include this option in generated {@link java.lang.Object#toString()} method
         *
         * @param includeInToString whether to include in the {@code toString} method
         * @return updated builder instance
         * @see #includeInToString()
         */
        public BUILDER includeInToString(boolean includeInToString) {
            this.includeInToString = includeInToString;
            return self();
        }

        /**
         * Whether to include this option in generated {@link java.lang.Object#equals(Object)}
         * and {@link java.lang.Object#hashCode()} methods.
         *
         * @param includeInEqualsAndHashCode whether to include in the {@code equals} and {@code hashCode} methods
         * @return updated builder instance
         * @see #includeInEqualsAndHashCode()
         */
        public BUILDER includeInEqualsAndHashCode(boolean includeInEqualsAndHashCode) {
            this.includeInEqualsAndHashCode = includeInEqualsAndHashCode;
            return self();
        }

        /**
         * Whether this option is confidential (i.e. we should not print the value in {@code toString} method).
         *
         * @param confidential whether this option is confidential
         * @return updated builder instance
         * @see #confidential()
         */
        public BUILDER confidential(boolean confidential) {
            this.confidential = confidential;
            return self();
        }

        /**
         * Whether this option should be loaded from {@code ServiceRegistry}.
         *
         * @param registryService whether this option should be loaded from {@code ServiceRegistry}
         * @return updated builder instance
         * @see #registryService()
         */
        public BUILDER registryService(boolean registryService) {
            this.registryService = registryService;
            return self();
        }

        /**
         * Whether this {@link java.util.Map} option is expected to have the same generic type for key and value.
         * For example, for a {@code Map<Class<?>, Instance<?>>} that has {@code sameGeneric} set to {@code true}, we would
         * generate singular method with signature {@code <T> put(Class<T>, Instance<T>}.
         *
         * @param sameGeneric whether a map has the same generic in key and value for a single mapping
         * @return updated builder instance
         * @see #sameGeneric()
         */
        public BUILDER sameGeneric(boolean sameGeneric) {
            this.sameGeneric = sameGeneric;
            return self();
        }

        /**
         * Will be true if:
         * <ul>
         *     <li>an {@code Option.Required} is present in blueprint</li>
         *     <li>the return type on blueprint is not {@link java.util.Optional} or a collection, and we do not support null</li>
         * </ul>
         *
         * @param required whether the option is required
         * @return updated builder instance
         * @see #required()
         */
        public BUILDER required(boolean required) {
            this.required = required;
            return self();
        }

        /**
         * List of qualifiers for this option.
         *
         * @param qualifiers service registry qualifiers defined on this option (to be used when getting a service registry instance)
         * @return updated builder instance
         * @see #qualifiers()
         */
        public BUILDER qualifiers(List<? extends Annotation> qualifiers) {
            Objects.requireNonNull(qualifiers);
            this.isQualifiersMutated = true;
            this.qualifiers.clear();
            this.qualifiers.addAll(qualifiers);
            return self();
        }

        /**
         * List of qualifiers for this option.
         *
         * @param qualifiers service registry qualifiers defined on this option (to be used when getting a service registry instance)
         * @return updated builder instance
         * @see #qualifiers()
         */
        public BUILDER addQualifiers(List<? extends Annotation> qualifiers) {
            Objects.requireNonNull(qualifiers);
            this.isQualifiersMutated = true;
            this.qualifiers.addAll(qualifiers);
            return self();
        }

        /**
         * List of qualifiers for this option.
         *
         * @param qualifier service registry qualifiers defined on this option (to be used when getting a service registry instance)
         * @return updated builder instance
         * @see #qualifiers()
         */
        public BUILDER addQualifier(Annotation qualifier) {
            Objects.requireNonNull(qualifier);
            this.qualifiers.add(qualifier);
            this.isQualifiersMutated = true;
            return self();
        }

        /**
         * List of qualifiers for this option.
         *
         * @param consumer consumer of builder for service registry qualifiers defined on this option (to be used when getting a service registry instance)
         * @return updated builder instance
         * @see #qualifiers()
         */
        public BUILDER addQualifier(Consumer<Annotation.Builder> consumer) {
            Objects.requireNonNull(consumer);
            var builder = Annotation.builder();
            consumer.accept(builder);
            this.qualifiers.add(builder.build());
            return self();
        }

        /**
         * List of allowed values for this option.
         *
         * @param allowedValues allowed values
         * @return updated builder instance
         * @see #allowedValues()
         */
        public BUILDER allowedValues(List<? extends OptionAllowedValue> allowedValues) {
            Objects.requireNonNull(allowedValues);
            this.isAllowedValuesMutated = true;
            this.allowedValues.clear();
            this.allowedValues.addAll(allowedValues);
            return self();
        }

        /**
         * List of allowed values for this option.
         *
         * @param allowedValues allowed values
         * @return updated builder instance
         * @see #allowedValues()
         */
        public BUILDER addAllowedValues(List<? extends OptionAllowedValue> allowedValues) {
            Objects.requireNonNull(allowedValues);
            this.isAllowedValuesMutated = true;
            this.allowedValues.addAll(allowedValues);
            return self();
        }

        /**
         * List of allowed values for this option.
         *
         * @param allowedValue allowed values
         * @return updated builder instance
         * @see #allowedValues()
         */
        public BUILDER addAllowedValue(OptionAllowedValue allowedValue) {
            Objects.requireNonNull(allowedValue);
            this.allowedValues.add(allowedValue);
            this.isAllowedValuesMutated = true;
            return self();
        }

        /**
         * List of allowed values for this option.
         *
         * @param consumer consumer of builder for allowed values
         * @return updated builder instance
         * @see #allowedValues()
         */
        public BUILDER addAllowedValue(Consumer<OptionAllowedValue.Builder> consumer) {
            Objects.requireNonNull(consumer);
            var builder = OptionAllowedValue.builder();
            consumer.accept(builder);
            this.allowedValues.add(builder.build());
            return self();
        }

        /**
         * Clear existing value of this property.
         *
         * @return updated builder instance
         * @see #defaultValue()
         */
        public BUILDER clearDefaultValue() {
            this.defaultValue = null;
            return self();
        }

        /**
         * Default value for this option, a consumer of the field content builder.
         *
         * @param defaultValue default value consumer
         * @return updated builder instance
         * @see #defaultValue()
         */
        public BUILDER defaultValue(Consumer<ContentBuilder<?>> defaultValue) {
            Objects.requireNonNull(defaultValue);
            this.defaultValue = defaultValue;
            return self();
        }

        /**
         * Clear existing value of this property.
         *
         * @return updated builder instance
         * @see #configured()
         */
        public BUILDER clearConfigured() {
            this.configured = null;
            return self();
        }

        /**
         * Details about configurability of this option.
         *
         * @param configured configured setup if configured
         * @return updated builder instance
         * @see #configured()
         */
        public BUILDER configured(OptionConfigured configured) {
            Objects.requireNonNull(configured);
            this.configured = configured;
            return self();
        }

        /**
         * Details about configurability of this option.
         *
         * @param consumer consumer of builder
         * @return updated builder instance
         * @see #configured()
         */
        public BUILDER configured(Consumer<OptionConfigured.Builder> consumer) {
            Objects.requireNonNull(consumer);
            var builder = OptionConfigured.builder();
            consumer.accept(builder);
            this.configured(builder.build());
            return self();
        }

        /**
         * Clear existing value of this property.
         *
         * @return updated builder instance
         * @see #deprecation()
         */
        public BUILDER clearDeprecation() {
            this.deprecation = null;
            return self();
        }

        /**
         * Deprecation details.
         *
         * @param deprecation deprecation details, if present
         * @return updated builder instance
         * @see #deprecation()
         */
        public BUILDER deprecation(OptionDeprecation deprecation) {
            Objects.requireNonNull(deprecation);
            this.deprecation = deprecation;
            return self();
        }

        /**
         * Deprecation details.
         *
         * @param consumer consumer of builder
         * @return updated builder instance
         * @see #deprecation()
         */
        public BUILDER deprecation(Consumer<OptionDeprecation.Builder> consumer) {
            Objects.requireNonNull(consumer);
            var builder = OptionDeprecation.builder();
            consumer.accept(builder);
            this.deprecation(builder.build());
            return self();
        }

        /**
         * Clear existing value of this property.
         *
         * @return updated builder instance
         * @see #provider()
         */
        public BUILDER clearProvider() {
            this.provider = null;
            return self();
        }

        /**
         * Provider details.
         *
         * @param provider provider details, if present
         * @return updated builder instance
         * @see #provider()
         */
        public BUILDER provider(OptionProvider provider) {
            Objects.requireNonNull(provider);
            this.provider = provider;
            return self();
        }

        /**
         * Provider details.
         *
         * @param consumer consumer of builder
         * @return updated builder instance
         * @see #provider()
         */
        public BUILDER provider(Consumer<OptionProvider.Builder> consumer) {
            Objects.requireNonNull(consumer);
            var builder = OptionProvider.builder();
            consumer.accept(builder);
            this.provider(builder.build());
            return self();
        }

        /**
         * Clear existing value of this property.
         *
         * @return updated builder instance
         * @see #singular()
         */
        public BUILDER clearSingular() {
            this.singular = null;
            return self();
        }

        /**
         * Singular option details.
         *
         * @param singular singular setter name and related information, if present
         * @return updated builder instance
         * @see #singular()
         */
        public BUILDER singular(OptionSingular singular) {
            Objects.requireNonNull(singular);
            this.singular = singular;
            return self();
        }

        /**
         * Singular option details.
         *
         * @param consumer consumer of builder
         * @return updated builder instance
         * @see #singular()
         */
        public BUILDER singular(Consumer<OptionSingular.Builder> consumer) {
            Objects.requireNonNull(consumer);
            var builder = OptionSingular.builder();
            consumer.accept(builder);
            this.singular(builder.build());
            return self();
        }

        /**
         * Access modifier of generated setter methods on builder.
         * Note that prototype methods are declared in an interface, so these must always be public.
         *
         * @param accessModifier access modifier to use
         * @return updated builder instance
         * @see #accessModifier()
         */
        public BUILDER accessModifier(AccessModifier accessModifier) {
            Objects.requireNonNull(accessModifier);
            this.accessModifier = accessModifier;
            return self();
        }

        /**
         * Clear existing value of this property.
         *
         * @return updated builder instance
         * @see #builderInfo()
         */
        public BUILDER clearBuilderInfo() {
            this.builderInfo = null;
            return self();
        }

        /**
         * If the option has a builder, return its information.
         *
         * @param builderInfo builder information, if present
         * @return updated builder instance
         * @see #builderInfo()
         */
        public BUILDER builderInfo(OptionBuilder builderInfo) {
            Objects.requireNonNull(builderInfo);
            this.builderInfo = builderInfo;
            return self();
        }

        /**
         * If the option has a builder, return its information.
         *
         * @param consumer consumer of builder
         * @return updated builder instance
         * @see #builderInfo()
         */
        public BUILDER builderInfo(Consumer<OptionBuilder.Builder> consumer) {
            Objects.requireNonNull(consumer);
            var builder = OptionBuilder.builder();
            consumer.accept(builder);
            this.builderInfo(builder.build());
            return self();
        }

        /**
         *
         *
         * @param annotations
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
         *
         *
         * @param annotations
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
         *
         *
         * @param annotation
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
         *
         *
         * @param consumer consumer of builder for
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
         *
         *
         * @param inheritedAnnotations
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
         *
         *
         * @param inheritedAnnotations
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
         *
         *
         * @param inheritedAnnotation
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
         *
         *
         * @param consumer consumer of builder for
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
         * Blueprint method if created from blueprint.
         * This may be also a method on a non-blueprint interface, in case the blueprint extends from it.
         * This must be filled in to generate the {@link java.lang.Override} annotation on the generated method.
         *
         * @return blueprint method, if present
         */
        public Optional<TypedElementInfo> blueprintMethod() {
            return Optional.ofNullable(blueprintMethod);
        }

        /**
         * Prototype getter definition.
         * This method is always abstract (interface method).
         *
         * @return prototype getter
         */
        public Optional<TypedElementInfo> getter() {
            return Optional.ofNullable(getter);
        }

        /**
         * Builder getter definition.
         * For non-collection types, this always returns an {@link java.util.Optional}, unless there is a default value
         * defined for a non-optional option.
         *
         * @return builder getter
         */
        public Optional<TypedElementInfo> builderGetter() {
            return Optional.ofNullable(builderGetter);
        }

        /**
         * Builder setter definition.
         * This is a setter for option's declared type, unless the type is {@link java.util.Optional}.
         * For optional options, the setter will have a non-optional parameter, and an unset method is generated as well.
         *
         * @return builder setter
         */
        public Optional<TypedElementInfo> setter() {
            return Optional.ofNullable(setter);
        }

        /**
         * If an option method returns {@link java.util.Optional}, a setter will be created
         * without the optional parameter (as {@link #setter()}, and another one with an optional parameter for
         * copy methods.
         *
         * @return setter with optional parameter, if present
         */
        public Optional<TypedElementInfo> setterForOptional() {
            return Optional.ofNullable(setterForOptional);
        }

        /**
         * Getter that is generated on the implementation class.
         * As the implementation class implements the prototype, this method is always annotated
         * with {@link java.lang.Override}.
         *
         * @return implementation getter
         */
        public Optional<TypedElementInfo> implGetter() {
            return Optional.ofNullable(implGetter);
        }

        /**
         * Option name.
         *
         * @return name of this option
         */
        public Optional<String> name() {
            return Optional.ofNullable(name);
        }

        /**
         * The return type of the blueprint method, or the type expected in getter of the option.
         *
         * @return declared type
         */
        public Optional<TypeName> declaredType() {
            return Optional.ofNullable(declaredType);
        }

        /**
         * Option decorator type.
         *
         * @return type of the option decorator, if present
         */
        public Optional<TypeName> decorator() {
            return Optional.ofNullable(decorator);
        }

        /**
         * Whether to include this option in generated {@link java.lang.Object#toString()} method
         *
         * @return whether to include in the {@code toString} method
         */
        public boolean includeInToString() {
            return includeInToString;
        }

        /**
         * Whether to include this option in generated {@link java.lang.Object#equals(Object)}
         * and {@link java.lang.Object#hashCode()} methods.
         *
         * @return whether to include in the {@code equals} and {@code hashCode} methods
         */
        public boolean includeInEqualsAndHashCode() {
            return includeInEqualsAndHashCode;
        }

        /**
         * Whether this option is confidential (i.e. we should not print the value in {@code toString} method).
         *
         * @return whether this option is confidential
         */
        public boolean confidential() {
            return confidential;
        }

        /**
         * Whether this option should be loaded from {@code ServiceRegistry}.
         *
         * @return whether this option should be loaded from {@code ServiceRegistry}
         */
        public boolean registryService() {
            return registryService;
        }

        /**
         * Whether this {@link java.util.Map} option is expected to have the same generic type for key and value.
         * For example, for a {@code Map<Class<?>, Instance<?>>} that has {@code sameGeneric} set to {@code true}, we would
         * generate singular method with signature {@code <T> put(Class<T>, Instance<T>}.
         *
         * @return whether a map has the same generic in key and value for a single mapping
         */
        public boolean sameGeneric() {
            return sameGeneric;
        }

        /**
         * Will be true if:
         * <ul>
         *     <li>an {@code Option.Required} is present in blueprint</li>
         *     <li>the return type on blueprint is not {@link java.util.Optional} or a collection, and we do not support null</li>
         * </ul>
         *
         * @return whether the option is required
         */
        public boolean required() {
            return required;
        }

        /**
         * List of qualifiers for this option.
         *
         * @return service registry qualifiers defined on this option (to be used when getting a service registry instance)
         */
        public List<Annotation> qualifiers() {
            return qualifiers;
        }

        /**
         * List of allowed values for this option.
         *
         * @return allowed values
         */
        public List<OptionAllowedValue> allowedValues() {
            return allowedValues;
        }

        /**
         * Default value for this option, a consumer of the field content builder.
         *
         * @return default value consumer
         */
        public Optional<Consumer<ContentBuilder<?>>> defaultValue() {
            return Optional.ofNullable(defaultValue);
        }

        /**
         * Details about configurability of this option.
         *
         * @return configured setup if configured
         */
        public Optional<OptionConfigured> configured() {
            return Optional.ofNullable(configured);
        }

        /**
         * Deprecation details.
         *
         * @return deprecation details, if present
         */
        public Optional<OptionDeprecation> deprecation() {
            return Optional.ofNullable(deprecation);
        }

        /**
         * Provider details.
         *
         * @return provider details, if present
         */
        public Optional<OptionProvider> provider() {
            return Optional.ofNullable(provider);
        }

        /**
         * Singular option details.
         *
         * @return singular setter name and related information, if present
         */
        public Optional<OptionSingular> singular() {
            return Optional.ofNullable(singular);
        }

        /**
         * Access modifier of generated setter methods on builder.
         * Note that prototype methods are declared in an interface, so these must always be public.
         *
         * @return access modifier to use
         */
        public AccessModifier accessModifier() {
            return accessModifier;
        }

        /**
         * If the option has a builder, return its information.
         *
         * @return builder information, if present
         */
        public Optional<OptionBuilder> builderInfo() {
            return Optional.ofNullable(builderInfo);
        }

        /**
         *
         */
        public List<Annotation> annotations() {
            return annotations;
        }

        /**
         *
         */
        public List<Annotation> inheritedAnnotations() {
            return inheritedAnnotations;
        }

        @Override
        public String toString() {
            return "OptionInfoBuilder{"
                    + "blueprintMethod=" + blueprintMethod + ","
                    + "getter=" + getter + ","
                    + "builderGetter=" + builderGetter + ","
                    + "setter=" + setter + ","
                    + "setterForOptional=" + setterForOptional + ","
                    + "implGetter=" + implGetter + ","
                    + "name=" + name + ","
                    + "declaredType=" + declaredType + ","
                    + "decorator=" + decorator + ","
                    + "includeInToString=" + includeInToString + ","
                    + "includeInEqualsAndHashCode=" + includeInEqualsAndHashCode + ","
                    + "confidential=" + confidential + ","
                    + "registryService=" + registryService + ","
                    + "sameGeneric=" + sameGeneric + ","
                    + "required=" + required + ","
                    + "qualifiers=" + qualifiers + ","
                    + "allowedValues=" + allowedValues + ","
                    + "defaultValue=" + defaultValue + ","
                    + "configured=" + configured + ","
                    + "deprecation=" + deprecation + ","
                    + "provider=" + provider + ","
                    + "singular=" + singular + ","
                    + "accessModifier=" + accessModifier + ","
                    + "builderInfo=" + builderInfo + ","
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
            if (getter == null) {
                collector.fatal(getClass(), "Property \"getter\" must not be null, but not set");
            }
            if (builderGetter == null) {
                collector.fatal(getClass(), "Property \"builderGetter\" must not be null, but not set");
            }
            if (setter == null) {
                collector.fatal(getClass(), "Property \"setter\" must not be null, but not set");
            }
            if (implGetter == null) {
                collector.fatal(getClass(), "Property \"implGetter\" must not be null, but not set");
            }
            if (name == null) {
                collector.fatal(getClass(), "Property \"name\" must not be null, but not set");
            }
            if (declaredType == null) {
                collector.fatal(getClass(), "Property \"declaredType\" must not be null, but not set");
            }
            collector.collect().checkValid();
        }

        /**
         * Blueprint method if created from blueprint.
         * This may be also a method on a non-blueprint interface, in case the blueprint extends from it.
         * This must be filled in to generate the {@link java.lang.Override} annotation on the generated method.
         *
         * @param blueprintMethod blueprint method, if present
         * @return updated builder instance
         * @see #blueprintMethod()
         */
        BUILDER blueprintMethod(Optional<? extends TypedElementInfo> blueprintMethod) {
            Objects.requireNonNull(blueprintMethod);
            this.blueprintMethod = blueprintMethod.map(io.helidon.common.types.TypedElementInfo.class::cast).orElse(this.blueprintMethod);
            return self();
        }

        /**
         * If an option method returns {@link java.util.Optional}, a setter will be created
         * without the optional parameter (as {@link #setter()}, and another one with an optional parameter for
         * copy methods.
         *
         * @param setterForOptional setter with optional parameter, if present
         * @return updated builder instance
         * @see #setterForOptional()
         */
        BUILDER setterForOptional(Optional<? extends TypedElementInfo> setterForOptional) {
            Objects.requireNonNull(setterForOptional);
            this.setterForOptional = setterForOptional.map(io.helidon.common.types.TypedElementInfo.class::cast).orElse(this.setterForOptional);
            return self();
        }

        /**
         * Option decorator type.
         *
         * @param decorator type of the option decorator, if present
         * @return updated builder instance
         * @see #decorator()
         */
        BUILDER decorator(Optional<? extends TypeName> decorator) {
            Objects.requireNonNull(decorator);
            this.decorator = decorator.map(io.helidon.common.types.TypeName.class::cast).orElse(this.decorator);
            return self();
        }

        /**
         * Default value for this option, a consumer of the field content builder.
         *
         * @param defaultValue default value consumer
         * @return updated builder instance
         * @see #defaultValue()
         */
        @SuppressWarnings("unchecked")
        BUILDER defaultValue(Optional<Consumer<ContentBuilder<?>>> defaultValue) {
            Objects.requireNonNull(defaultValue);
            this.defaultValue = defaultValue.map(java.util.function.Consumer.class::cast).orElse(this.defaultValue);
            return self();
        }

        /**
         * Details about configurability of this option.
         *
         * @param configured configured setup if configured
         * @return updated builder instance
         * @see #configured()
         */
        BUILDER configured(Optional<? extends OptionConfigured> configured) {
            Objects.requireNonNull(configured);
            this.configured = configured.map(OptionConfigured.class::cast).orElse(this.configured);
            return self();
        }

        /**
         * Deprecation details.
         *
         * @param deprecation deprecation details, if present
         * @return updated builder instance
         * @see #deprecation()
         */
        BUILDER deprecation(Optional<? extends OptionDeprecation> deprecation) {
            Objects.requireNonNull(deprecation);
            this.deprecation = deprecation.map(OptionDeprecation.class::cast).orElse(this.deprecation);
            return self();
        }

        /**
         * Provider details.
         *
         * @param provider provider details, if present
         * @return updated builder instance
         * @see #provider()
         */
        BUILDER provider(Optional<? extends OptionProvider> provider) {
            Objects.requireNonNull(provider);
            this.provider = provider.map(OptionProvider.class::cast).orElse(this.provider);
            return self();
        }

        /**
         * Singular option details.
         *
         * @param singular singular setter name and related information, if present
         * @return updated builder instance
         * @see #singular()
         */
        BUILDER singular(Optional<? extends OptionSingular> singular) {
            Objects.requireNonNull(singular);
            this.singular = singular.map(OptionSingular.class::cast).orElse(this.singular);
            return self();
        }

        /**
         * If the option has a builder, return its information.
         *
         * @param builderInfo builder information, if present
         * @return updated builder instance
         * @see #builderInfo()
         */
        BUILDER builderInfo(Optional<? extends OptionBuilder> builderInfo) {
            Objects.requireNonNull(builderInfo);
            this.builderInfo = builderInfo.map(OptionBuilder.class::cast).orElse(this.builderInfo);
            return self();
        }

        /**
         * Generated implementation of the prototype, can be extended by descendant prototype implementations.
         */
        protected static class OptionInfoImpl implements OptionInfo {

            private final AccessModifier accessModifier;
            private final boolean confidential;
            private final boolean includeInEqualsAndHashCode;
            private final boolean includeInToString;
            private final boolean registryService;
            private final boolean required;
            private final boolean sameGeneric;
            private final List<OptionAllowedValue> allowedValues;
            private final List<Annotation> annotations;
            private final List<Annotation> inheritedAnnotations;
            private final List<Annotation> qualifiers;
            private final Optional<OptionBuilder> builderInfo;
            private final Optional<OptionConfigured> configured;
            private final Optional<OptionDeprecation> deprecation;
            private final Optional<OptionProvider> provider;
            private final Optional<OptionSingular> singular;
            private final Optional<TypeName> decorator;
            private final Optional<TypedElementInfo> blueprintMethod;
            private final Optional<TypedElementInfo> setterForOptional;
            private final Optional<Consumer<ContentBuilder<?>>> defaultValue;
            private final String name;
            private final TypedElementInfo builderGetter;
            private final TypedElementInfo getter;
            private final TypedElementInfo implGetter;
            private final TypedElementInfo setter;
            private final TypeName declaredType;

            /**
             * Create an instance providing a builder.
             *
             * @param builder extending builder base of this prototype
             */
            protected OptionInfoImpl(OptionInfo.BuilderBase<?, ?> builder) {
                this.blueprintMethod = builder.blueprintMethod().map(Function.identity());
                this.getter = builder.getter().get();
                this.builderGetter = builder.builderGetter().get();
                this.setter = builder.setter().get();
                this.setterForOptional = builder.setterForOptional().map(Function.identity());
                this.implGetter = builder.implGetter().get();
                this.name = builder.name().get();
                this.declaredType = builder.declaredType().get();
                this.decorator = builder.decorator().map(Function.identity());
                this.includeInToString = builder.includeInToString();
                this.includeInEqualsAndHashCode = builder.includeInEqualsAndHashCode();
                this.confidential = builder.confidential();
                this.registryService = builder.registryService();
                this.sameGeneric = builder.sameGeneric();
                this.required = builder.required();
                this.qualifiers = List.copyOf(builder.qualifiers());
                this.allowedValues = List.copyOf(builder.allowedValues());
                this.defaultValue = builder.defaultValue().map(Function.identity());
                this.configured = builder.configured().map(Function.identity());
                this.deprecation = builder.deprecation().map(Function.identity());
                this.provider = builder.provider().map(Function.identity());
                this.singular = builder.singular().map(Function.identity());
                this.accessModifier = builder.accessModifier();
                this.builderInfo = builder.builderInfo().map(Function.identity());
                this.annotations = List.copyOf(builder.annotations());
                this.inheritedAnnotations = List.copyOf(builder.inheritedAnnotations());
            }

            @Override
            public Optional<TypedElementInfo> blueprintMethod() {
                return blueprintMethod;
            }

            @Override
            public TypedElementInfo getter() {
                return getter;
            }

            @Override
            public TypedElementInfo builderGetter() {
                return builderGetter;
            }

            @Override
            public TypedElementInfo setter() {
                return setter;
            }

            @Override
            public Optional<TypedElementInfo> setterForOptional() {
                return setterForOptional;
            }

            @Override
            public TypedElementInfo implGetter() {
                return implGetter;
            }

            @Override
            public String name() {
                return name;
            }

            @Override
            public TypeName declaredType() {
                return declaredType;
            }

            @Override
            public Optional<TypeName> decorator() {
                return decorator;
            }

            @Override
            public boolean includeInToString() {
                return includeInToString;
            }

            @Override
            public boolean includeInEqualsAndHashCode() {
                return includeInEqualsAndHashCode;
            }

            @Override
            public boolean confidential() {
                return confidential;
            }

            @Override
            public boolean registryService() {
                return registryService;
            }

            @Override
            public boolean sameGeneric() {
                return sameGeneric;
            }

            @Override
            public boolean required() {
                return required;
            }

            @Override
            public List<Annotation> qualifiers() {
                return qualifiers;
            }

            @Override
            public List<OptionAllowedValue> allowedValues() {
                return allowedValues;
            }

            @Override
            public Optional<Consumer<ContentBuilder<?>>> defaultValue() {
                return defaultValue;
            }

            @Override
            public Optional<OptionConfigured> configured() {
                return configured;
            }

            @Override
            public Optional<OptionDeprecation> deprecation() {
                return deprecation;
            }

            @Override
            public Optional<OptionProvider> provider() {
                return provider;
            }

            @Override
            public Optional<OptionSingular> singular() {
                return singular;
            }

            @Override
            public AccessModifier accessModifier() {
                return accessModifier;
            }

            @Override
            public Optional<OptionBuilder> builderInfo() {
                return builderInfo;
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
                return "OptionInfo{"
                        + "blueprintMethod=" + blueprintMethod + ","
                        + "getter=" + getter + ","
                        + "builderGetter=" + builderGetter + ","
                        + "setter=" + setter + ","
                        + "setterForOptional=" + setterForOptional + ","
                        + "implGetter=" + implGetter + ","
                        + "name=" + name + ","
                        + "declaredType=" + declaredType + ","
                        + "decorator=" + decorator + ","
                        + "includeInToString=" + includeInToString + ","
                        + "includeInEqualsAndHashCode=" + includeInEqualsAndHashCode + ","
                        + "confidential=" + confidential + ","
                        + "registryService=" + registryService + ","
                        + "sameGeneric=" + sameGeneric + ","
                        + "required=" + required + ","
                        + "qualifiers=" + qualifiers + ","
                        + "allowedValues=" + allowedValues + ","
                        + "defaultValue=" + defaultValue + ","
                        + "configured=" + configured + ","
                        + "deprecation=" + deprecation + ","
                        + "provider=" + provider + ","
                        + "singular=" + singular + ","
                        + "accessModifier=" + accessModifier + ","
                        + "builderInfo=" + builderInfo + ","
                        + "annotations=" + annotations + ","
                        + "inheritedAnnotations=" + inheritedAnnotations
                        + "}";
            }

            @Override
            public boolean equals(Object o) {
                if (o == this) {
                    return true;
                }
                if (!(o instanceof OptionInfo other)) {
                    return false;
                }
                return Objects.equals(blueprintMethod, other.blueprintMethod())
                    && Objects.equals(getter, other.getter())
                    && Objects.equals(builderGetter, other.builderGetter())
                    && Objects.equals(setter, other.setter())
                    && Objects.equals(setterForOptional, other.setterForOptional())
                    && Objects.equals(implGetter, other.implGetter())
                    && Objects.equals(name, other.name())
                    && Objects.equals(declaredType, other.declaredType())
                    && Objects.equals(decorator, other.decorator())
                    && includeInToString == other.includeInToString()
                    && includeInEqualsAndHashCode == other.includeInEqualsAndHashCode()
                    && confidential == other.confidential()
                    && registryService == other.registryService()
                    && sameGeneric == other.sameGeneric()
                    && required == other.required()
                    && Objects.equals(qualifiers, other.qualifiers())
                    && Objects.equals(allowedValues, other.allowedValues())
                    && Objects.equals(defaultValue, other.defaultValue())
                    && Objects.equals(configured, other.configured())
                    && Objects.equals(deprecation, other.deprecation())
                    && Objects.equals(provider, other.provider())
                    && Objects.equals(singular, other.singular())
                    && Objects.equals(accessModifier, other.accessModifier())
                    && Objects.equals(builderInfo, other.builderInfo())
                    && Objects.equals(annotations, other.annotations())
                    && Objects.equals(inheritedAnnotations, other.inheritedAnnotations());
            }

            @Override
            public int hashCode() {
                return Objects.hash(blueprintMethod, getter, builderGetter, setter, setterForOptional, implGetter, name, declaredType, decorator, includeInToString, includeInEqualsAndHashCode, confidential, registryService, sameGeneric, required, qualifiers, allowedValues, defaultValue, configured, deprecation, provider, singular, accessModifier, builderInfo, annotations, inheritedAnnotations);
            }

        }

    }

    /**
     * Fluent API builder for {@link OptionInfo}.
     */
    class Builder extends OptionInfo.BuilderBase<OptionInfo.Builder, OptionInfo>
            implements io.helidon.common.Builder<OptionInfo.Builder, OptionInfo> {

        private Builder() {
        }

        @Override
        public OptionInfo buildPrototype() {
            preBuildPrototype();
            validatePrototype();
            return new OptionInfoImpl(this);
        }

        @Override
        public OptionInfo build() {
            return buildPrototype();
        }

    }

}
