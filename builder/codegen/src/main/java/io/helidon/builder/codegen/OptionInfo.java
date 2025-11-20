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
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.Annotated;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypedElementInfo;

/**
 * Model of a prototype/builder option.
 *
 * @see #builder()
 */
public interface OptionInfo extends Prototype.Api, Annotated {

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
    static Builder builder(OptionInfo instance) {
        return OptionInfo.builder().from(instance);
    }

    /**
     * Blueprint method if created from blueprint, or interface method if inherited from non-blueprint interface.
     * Empty in case this is a "synthetic" option.
     *
     * @return interface method, if present
     */
    Optional<TypedElementInfo> interfaceMethod();

    /**
     * Type that declares this option.
     * This may be the blueprint type, or an interface type.
     * In case this is a "synthetic" option, there is not type to use.
     *
     * @return type that declares this option, if present
     */
    Optional<TypeInfo> declaringType();

    /**
     * Option name.
     *
     * @return name of this option
     */
    String name();

    /**
     * Name of the getter methods.
     *
     * @return getter method name
     */
    String getterName();

    /**
     * Name of the setter method(s).
     *
     * @return setter method name
     */
    String setterName();

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
     * Whether to include this option in generated {@link Object#toString()} method.
     *
     * @return whether to include in the {@code toString} method
     */
    boolean includeInToString();

    /**
     * Whether to include this option in generated {@link Object#equals(Object)}
     * and {@link Object#hashCode()} methods.
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
     * Whether this is a required option.
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
     * Set to {@code true} if this option is only available on the builder.
     * In such a case the prototype and implementation will not have this option.
     *
     * @return builder option only
     */
    boolean builderOptionOnly();

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
     * Custom runtime type factory method.
     *
     * @return runtime type factory method, if present
     */
    Optional<RuntimeTypeInfo> runtimeType();

    /**
     * Description of this option, used in Javadoc as the main text if defined.
     *
     * @return description, if present
     */
    Optional<String> description();

    /**
     * Parameter/return type description, used in Javadoc as the param/return description.
     *
     * @return parameter description, if present
     */
    Optional<String> paramDescription();

    /**
     * Fluent API builder base for {@link io.helidon.builder.codegen.OptionInfo}.
     *
     * @param <BUILDER>   type of the builder extending this abstract builder
     * @param <PROTOTYPE> type of the prototype interface that would be built by {@link #buildPrototype()}
     */
    abstract class BuilderBase<BUILDER extends BuilderBase<BUILDER, PROTOTYPE>, PROTOTYPE extends OptionInfo>
            implements Prototype.Builder<BUILDER, PROTOTYPE> {

        private final List<OptionAllowedValue> allowedValues = new ArrayList<>();
        private final List<Annotation> annotations = new ArrayList<>();
        private final List<Annotation> inheritedAnnotations = new ArrayList<>();
        private final List<Annotation> qualifiers = new ArrayList<>();
        private AccessModifier accessModifier = AccessModifier.PUBLIC;
        private boolean builderOptionOnly;
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
        private RuntimeTypeInfo runtimeType;
        private String description;
        private String getterName;
        private String name;
        private String paramDescription;
        private String setterName;
        private TypedElementInfo interfaceMethod;
        private TypeInfo declaringType;
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
            interfaceMethod(prototype.interfaceMethod());
            declaringType(prototype.declaringType());
            name(prototype.name());
            getterName(prototype.getterName());
            setterName(prototype.setterName());
            declaredType(prototype.declaredType());
            decorator(prototype.decorator());
            includeInToString(prototype.includeInToString());
            includeInEqualsAndHashCode(prototype.includeInEqualsAndHashCode());
            confidential(prototype.confidential());
            registryService(prototype.registryService());
            sameGeneric(prototype.sameGeneric());
            required(prototype.required());
            builderOptionOnly(prototype.builderOptionOnly());
            if (!this.isQualifiersMutated) {
                this.qualifiers.clear();
            }
            addQualifiers(prototype.qualifiers());
            if (!this.isAllowedValuesMutated) {
                this.allowedValues.clear();
            }
            addAllowedValues(prototype.allowedValues());
            defaultValue(prototype.defaultValue());
            configured(prototype.configured());
            deprecation(prototype.deprecation());
            provider(prototype.provider());
            singular(prototype.singular());
            accessModifier(prototype.accessModifier());
            builderInfo(prototype.builderInfo());
            runtimeType(prototype.runtimeType());
            description(prototype.description());
            paramDescription(prototype.paramDescription());
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
            builder.interfaceMethod().ifPresent(this::interfaceMethod);
            builder.declaringType().ifPresent(this::declaringType);
            builder.name().ifPresent(this::name);
            builder.getterName().ifPresent(this::getterName);
            builder.setterName().ifPresent(this::setterName);
            builder.declaredType().ifPresent(this::declaredType);
            builder.decorator().ifPresent(this::decorator);
            includeInToString(builder.includeInToString());
            includeInEqualsAndHashCode(builder.includeInEqualsAndHashCode());
            confidential(builder.confidential());
            registryService(builder.registryService());
            sameGeneric(builder.sameGeneric());
            required(builder.required());
            builderOptionOnly(builder.builderOptionOnly());
            if (this.isQualifiersMutated) {
                if (builder.isQualifiersMutated) {
                    addQualifiers(builder.qualifiers());
                }
            } else {
                qualifiers(builder.qualifiers());
            }
            if (this.isAllowedValuesMutated) {
                if (builder.isAllowedValuesMutated) {
                    addAllowedValues(builder.allowedValues());
                }
            } else {
                allowedValues(builder.allowedValues());
            }
            builder.defaultValue().ifPresent(this::defaultValue);
            builder.configured().ifPresent(this::configured);
            builder.deprecation().ifPresent(this::deprecation);
            builder.provider().ifPresent(this::provider);
            builder.singular().ifPresent(this::singular);
            accessModifier(builder.accessModifier());
            builder.builderInfo().ifPresent(this::builderInfo);
            builder.runtimeType().ifPresent(this::runtimeType);
            builder.description().ifPresent(this::description);
            builder.paramDescription().ifPresent(this::paramDescription);
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
         * Clear existing value of interfaceMethod.
         *
         * @return updated builder instance
         * @see #interfaceMethod()
         */
        public BUILDER clearInterfaceMethod() {
            this.interfaceMethod = null;
            return self();
        }

        /**
         * Blueprint method if created from blueprint, or interface method if inherited from non-blueprint interface.
         * Empty in case this is a "synthetic" option.
         *
         * @param interfaceMethod interface method, if present
         * @return updated builder instance
         * @see #interfaceMethod()
         */
        public BUILDER interfaceMethod(TypedElementInfo interfaceMethod) {
            Objects.requireNonNull(interfaceMethod);
            this.interfaceMethod = interfaceMethod;
            return self();
        }

        /**
         * Blueprint method if created from blueprint, or interface method if inherited from non-blueprint interface.
         * Empty in case this is a "synthetic" option.
         *
         * @param consumer consumer of builder of interface method, if present
         * @return updated builder instance
         * @see #interfaceMethod()
         */
        public BUILDER interfaceMethod(Consumer<TypedElementInfo.Builder> consumer) {
            Objects.requireNonNull(consumer);
            var builder = TypedElementInfo.builder();
            consumer.accept(builder);
            this.interfaceMethod(builder.build());
            return self();
        }

        /**
         * Blueprint method if created from blueprint, or interface method if inherited from non-blueprint interface.
         * Empty in case this is a "synthetic" option.
         *
         * @param supplier supplier of interface method, if present
         * @return updated builder instance
         * @see #interfaceMethod()
         */
        public BUILDER interfaceMethod(Supplier<? extends TypedElementInfo> supplier) {
            Objects.requireNonNull(supplier);
            this.interfaceMethod(supplier.get());
            return self();
        }

        /**
         * Clear existing value of declaringType.
         *
         * @return updated builder instance
         * @see #declaringType()
         */
        public BUILDER clearDeclaringType() {
            this.declaringType = null;
            return self();
        }

        /**
         * Type that declares this option.
         * This may be the blueprint type, or an interface type.
         * In case this is a "synthetic" option, there is not type to use.
         *
         * @param declaringType type that declares this option, if present
         * @return updated builder instance
         * @see #declaringType()
         */
        public BUILDER declaringType(TypeInfo declaringType) {
            Objects.requireNonNull(declaringType);
            this.declaringType = declaringType;
            return self();
        }

        /**
         * Type that declares this option.
         * This may be the blueprint type, or an interface type.
         * In case this is a "synthetic" option, there is not type to use.
         *
         * @param consumer consumer of builder of type that declares this option, if present
         * @return updated builder instance
         * @see #declaringType()
         */
        public BUILDER declaringType(Consumer<TypeInfo.Builder> consumer) {
            Objects.requireNonNull(consumer);
            var builder = TypeInfo.builder();
            consumer.accept(builder);
            this.declaringType(builder.build());
            return self();
        }

        /**
         * Type that declares this option.
         * This may be the blueprint type, or an interface type.
         * In case this is a "synthetic" option, there is not type to use.
         *
         * @param supplier supplier of type that declares this option, if present
         * @return updated builder instance
         * @see #declaringType()
         */
        public BUILDER declaringType(Supplier<? extends TypeInfo> supplier) {
            Objects.requireNonNull(supplier);
            this.declaringType(supplier.get());
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
         * Name of the getter methods.
         *
         * @param getterName getter method name
         * @return updated builder instance
         * @see #getterName()
         */
        public BUILDER getterName(String getterName) {
            Objects.requireNonNull(getterName);
            this.getterName = getterName;
            return self();
        }

        /**
         * Name of the setter method(s).
         *
         * @param setterName setter method name
         * @return updated builder instance
         * @see #setterName()
         */
        public BUILDER setterName(String setterName) {
            Objects.requireNonNull(setterName);
            this.setterName = setterName;
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
         * @param consumer consumer of builder of declared type
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
         * @param supplier supplier of declared type
         * @return updated builder instance
         * @see #declaredType()
         */
        public BUILDER declaredType(Supplier<? extends TypeName> supplier) {
            Objects.requireNonNull(supplier);
            this.declaredType(supplier.get());
            return self();
        }

        /**
         * Clear existing value of decorator.
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
         * @param consumer consumer of builder of type of the option decorator, if present
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
         * Option decorator type.
         *
         * @param supplier supplier of type of the option decorator, if present
         * @return updated builder instance
         * @see #decorator()
         */
        public BUILDER decorator(Supplier<? extends TypeName> supplier) {
            Objects.requireNonNull(supplier);
            this.decorator(supplier.get());
            return self();
        }

        /**
         * Whether to include this option in generated {@link Object#toString()} method.
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
         * Whether to include this option in generated {@link Object#equals(Object)}
         * and {@link Object#hashCode()} methods.
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
         * Whether this is a required option.
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
         * Set to {@code true} if this option is only available on the builder.
         * In such a case the prototype and implementation will not have this option.
         *
         * @param builderOptionOnly builder option only
         * @return updated builder instance
         * @see #builderOptionOnly()
         */
        public BUILDER builderOptionOnly(boolean builderOptionOnly) {
            this.builderOptionOnly = builderOptionOnly;
            return self();
        }

        /**
         * Clear all qualifiers.
         *
         * @return updated builder instance
         * @see #qualifiers()
         */
        public BUILDER clearQualifiers() {
            this.isQualifiersMutated = true;
            this.qualifiers.clear();
            return self();
        }

        /**
         * List of qualifiers for this option.
         *
         * @param qualifiers service registry qualifiers defined on this option (to be used when getting a service registry
         *                   instance)
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
         * @param qualifiers service registry qualifiers defined on this option (to be used when getting a service registry
         *                   instance)
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
         * @param qualifier add single service registry qualifiers defined on this option (to be used when getting a service
         *                  registry instance)
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
         * @param consumer consumer of builder for service registry qualifiers defined on this option (to be used when getting a
         *                 service registry instance)
         * @return updated builder instance
         * @see #qualifiers()
         */
        public BUILDER addQualifier(Consumer<Annotation.Builder> consumer) {
            Objects.requireNonNull(consumer);
            var builder = Annotation.builder();
            consumer.accept(builder);
            this.addQualifier(builder.build());
            return self();
        }

        /**
         * Clear all allowedValues.
         *
         * @return updated builder instance
         * @see #allowedValues()
         */
        public BUILDER clearAllowedValues() {
            this.isAllowedValuesMutated = true;
            this.allowedValues.clear();
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
         * @param allowedValue add single allowed values
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
            this.addAllowedValue(builder.build());
            return self();
        }

        /**
         * Clear existing value of defaultValue.
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
         * @param consumer consumer of builder of configured setup if configured
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
         * Details about configurability of this option.
         *
         * @param supplier supplier of configured setup if configured
         * @return updated builder instance
         * @see #configured()
         */
        public BUILDER configured(Supplier<? extends OptionConfigured> supplier) {
            Objects.requireNonNull(supplier);
            this.configured(supplier.get());
            return self();
        }

        /**
         * Clear existing value of deprecation.
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
         * @param consumer consumer of builder of deprecation details, if present
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
         * Deprecation details.
         *
         * @param supplier supplier of deprecation details, if present
         * @return updated builder instance
         * @see #deprecation()
         */
        public BUILDER deprecation(Supplier<? extends OptionDeprecation> supplier) {
            Objects.requireNonNull(supplier);
            this.deprecation(supplier.get());
            return self();
        }

        /**
         * Clear existing value of provider.
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
         * @param consumer consumer of builder of provider details, if present
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
         * Provider details.
         *
         * @param supplier supplier of provider details, if present
         * @return updated builder instance
         * @see #provider()
         */
        public BUILDER provider(Supplier<? extends OptionProvider> supplier) {
            Objects.requireNonNull(supplier);
            this.provider(supplier.get());
            return self();
        }

        /**
         * Clear existing value of singular.
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
         * @param consumer consumer of builder of singular setter name and related information, if present
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
         * Singular option details.
         *
         * @param supplier supplier of singular setter name and related information, if present
         * @return updated builder instance
         * @see #singular()
         */
        public BUILDER singular(Supplier<? extends OptionSingular> supplier) {
            Objects.requireNonNull(supplier);
            this.singular(supplier.get());
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
         * Clear existing value of builderInfo.
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
         * @param consumer consumer of builder of builder information, if present
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
         * If the option has a builder, return its information.
         *
         * @param supplier supplier of builder information, if present
         * @return updated builder instance
         * @see #builderInfo()
         */
        public BUILDER builderInfo(Supplier<? extends OptionBuilder> supplier) {
            Objects.requireNonNull(supplier);
            this.builderInfo(supplier.get());
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
         * Custom runtime type factory method.
         *
         * @param runtimeType runtime type factory method, if present
         * @return updated builder instance
         * @see #runtimeType()
         */
        public BUILDER runtimeType(RuntimeTypeInfo runtimeType) {
            Objects.requireNonNull(runtimeType);
            this.runtimeType = runtimeType;
            return self();
        }

        /**
         * Custom runtime type factory method.
         *
         * @param consumer consumer of builder of runtime type factory method, if present
         * @return updated builder instance
         * @see #runtimeType()
         */
        public BUILDER runtimeType(Consumer<RuntimeTypeInfo.Builder> consumer) {
            Objects.requireNonNull(consumer);
            var builder = RuntimeTypeInfo.builder();
            consumer.accept(builder);
            this.runtimeType(builder.build());
            return self();
        }

        /**
         * Custom runtime type factory method.
         *
         * @param supplier supplier of runtime type factory method, if present
         * @return updated builder instance
         * @see #runtimeType()
         */
        public BUILDER runtimeType(Supplier<? extends RuntimeTypeInfo> supplier) {
            Objects.requireNonNull(supplier);
            this.runtimeType(supplier.get());
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
         * Description of this option, used in Javadoc as the main text if defined.
         *
         * @param description description, if present
         * @return updated builder instance
         * @see #description()
         */
        public BUILDER description(String description) {
            Objects.requireNonNull(description);
            this.description = description;
            return self();
        }

        /**
         * Clear existing value of paramDescription.
         *
         * @return updated builder instance
         * @see #paramDescription()
         */
        public BUILDER clearParamDescription() {
            this.paramDescription = null;
            return self();
        }

        /**
         * Parameter/return type description, used in Javadoc as the param/return description.
         *
         * @param paramDescription parameter description, if present
         * @return updated builder instance
         * @see #paramDescription()
         */
        public BUILDER paramDescription(String paramDescription) {
            Objects.requireNonNull(paramDescription);
            this.paramDescription = paramDescription;
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
         * Blueprint method if created from blueprint, or interface method if inherited from non-blueprint interface.
         * Empty in case this is a "synthetic" option.
         *
         * @return interface method, if present
         */
        public Optional<TypedElementInfo> interfaceMethod() {
            return Optional.ofNullable(interfaceMethod);
        }

        /**
         * Type that declares this option.
         * This may be the blueprint type, or an interface type.
         * In case this is a "synthetic" option, there is not type to use.
         *
         * @return type that declares this option, if present
         */
        public Optional<TypeInfo> declaringType() {
            return Optional.ofNullable(declaringType);
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
         * Name of the getter methods.
         *
         * @return getter method name
         */
        public Optional<String> getterName() {
            return Optional.ofNullable(getterName);
        }

        /**
         * Name of the setter method(s).
         *
         * @return setter method name
         */
        public Optional<String> setterName() {
            return Optional.ofNullable(setterName);
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
         * Whether to include this option in generated {@link Object#toString()} method.
         *
         * @return whether to include in the {@code toString} method
         */
        public boolean includeInToString() {
            return includeInToString;
        }

        /**
         * Whether to include this option in generated {@link Object#equals(Object)}
         * and {@link Object#hashCode()} methods.
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
         * Whether this is a required option.
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
         * Set to {@code true} if this option is only available on the builder.
         * In such a case the prototype and implementation will not have this option.
         *
         * @return builder option only
         */
        public boolean builderOptionOnly() {
            return builderOptionOnly;
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
         * Custom runtime type factory method.
         *
         * @return runtime type factory method, if present
         */
        public Optional<RuntimeTypeInfo> runtimeType() {
            return Optional.ofNullable(runtimeType);
        }

        /**
         * Description of this option, used in Javadoc as the main text if defined.
         *
         * @return description, if present
         */
        public Optional<String> description() {
            return Optional.ofNullable(description);
        }

        /**
         * Parameter/return type description, used in Javadoc as the param/return description.
         *
         * @return parameter description, if present
         */
        public Optional<String> paramDescription() {
            return Optional.ofNullable(paramDescription);
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
            return "OptionInfoBuilder{"
                    + "interfaceMethod=" + interfaceMethod + ","
                    + "declaringType=" + declaringType + ","
                    + "name=" + name + ","
                    + "getterName=" + getterName + ","
                    + "setterName=" + setterName + ","
                    + "declaredType=" + declaredType + ","
                    + "decorator=" + decorator + ","
                    + "includeInToString=" + includeInToString + ","
                    + "includeInEqualsAndHashCode=" + includeInEqualsAndHashCode + ","
                    + "confidential=" + confidential + ","
                    + "registryService=" + registryService + ","
                    + "sameGeneric=" + sameGeneric + ","
                    + "required=" + required + ","
                    + "builderOptionOnly=" + builderOptionOnly + ","
                    + "qualifiers=" + qualifiers + ","
                    + "allowedValues=" + allowedValues + ","
                    + "defaultValue=" + defaultValue + ","
                    + "configured=" + configured + ","
                    + "deprecation=" + deprecation + ","
                    + "provider=" + provider + ","
                    + "singular=" + singular + ","
                    + "accessModifier=" + accessModifier + ","
                    + "builderInfo=" + builderInfo + ","
                    + "runtimeType=" + runtimeType + ","
                    + "description=" + description + ","
                    + "paramDescription=" + paramDescription + ","
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
            if (getterName == null) {
                collector.fatal(getClass(), "Property \"getterName\" must not be null, but not set");
            }
            if (setterName == null) {
                collector.fatal(getClass(), "Property \"setterName\" must not be null, but not set");
            }
            if (declaredType == null) {
                collector.fatal(getClass(), "Property \"declaredType\" must not be null, but not set");
            }
            collector.collect().checkValid();
        }

        /**
         * Blueprint method if created from blueprint, or interface method if inherited from non-blueprint interface.
         * Empty in case this is a "synthetic" option.
         *
         * @param interfaceMethod interface method, if present
         * @return updated builder instance
         * @see #interfaceMethod()
         */
        @SuppressWarnings("unchecked")
        BUILDER interfaceMethod(Optional<? extends TypedElementInfo> interfaceMethod) {
            Objects.requireNonNull(interfaceMethod);
            this.interfaceMethod = interfaceMethod.map(TypedElementInfo.class::cast).orElse(this.interfaceMethod);
            return self();
        }

        /**
         * Type that declares this option.
         * This may be the blueprint type, or an interface type.
         * In case this is a "synthetic" option, there is not type to use.
         *
         * @param declaringType type that declares this option, if present
         * @return updated builder instance
         * @see #declaringType()
         */
        @SuppressWarnings("unchecked")
        BUILDER declaringType(Optional<? extends TypeInfo> declaringType) {
            Objects.requireNonNull(declaringType);
            this.declaringType = declaringType.map(TypeInfo.class::cast).orElse(this.declaringType);
            return self();
        }

        /**
         * Option decorator type.
         *
         * @param decorator type of the option decorator, if present
         * @return updated builder instance
         * @see #decorator()
         */
        @SuppressWarnings("unchecked")
        BUILDER decorator(Optional<? extends TypeName> decorator) {
            Objects.requireNonNull(decorator);
            this.decorator = decorator.map(TypeName.class::cast).orElse(this.decorator);
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
            this.defaultValue = defaultValue.map(Consumer.class::cast).orElse(this.defaultValue);
            return self();
        }

        /**
         * Details about configurability of this option.
         *
         * @param configured configured setup if configured
         * @return updated builder instance
         * @see #configured()
         */
        @SuppressWarnings("unchecked")
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
        @SuppressWarnings("unchecked")
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
        @SuppressWarnings("unchecked")
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
        @SuppressWarnings("unchecked")
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
        @SuppressWarnings("unchecked")
        BUILDER builderInfo(Optional<? extends OptionBuilder> builderInfo) {
            Objects.requireNonNull(builderInfo);
            this.builderInfo = builderInfo.map(OptionBuilder.class::cast).orElse(this.builderInfo);
            return self();
        }

        /**
         * Custom runtime type factory method.
         *
         * @param runtimeType runtime type factory method, if present
         * @return updated builder instance
         * @see #runtimeType()
         */
        @SuppressWarnings("unchecked")
        BUILDER runtimeType(Optional<? extends RuntimeTypeInfo> runtimeType) {
            Objects.requireNonNull(runtimeType);
            this.runtimeType = runtimeType.map(RuntimeTypeInfo.class::cast).orElse(this.runtimeType);
            return self();
        }

        /**
         * Description of this option, used in Javadoc as the main text if defined.
         *
         * @param description description, if present
         * @return updated builder instance
         * @see #description()
         */
        BUILDER description(Optional<String> description) {
            Objects.requireNonNull(description);
            this.description = description.orElse(this.description);
            return self();
        }

        /**
         * Parameter/return type description, used in Javadoc as the param/return description.
         *
         * @param paramDescription parameter description, if present
         * @return updated builder instance
         * @see #paramDescription()
         */
        BUILDER paramDescription(Optional<String> paramDescription) {
            Objects.requireNonNull(paramDescription);
            this.paramDescription = paramDescription.orElse(this.paramDescription);
            return self();
        }

        /**
         * Generated implementation of the prototype, can be extended by descendant prototype implementations.
         */
        protected static class OptionInfoImpl implements OptionInfo {

            private final AccessModifier accessModifier;
            private final boolean builderOptionOnly;
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
            private final Optional<RuntimeTypeInfo> runtimeType;
            private final Optional<TypeInfo> declaringType;
            private final Optional<TypeName> decorator;
            private final Optional<TypedElementInfo> interfaceMethod;
            private final Optional<String> description;
            private final Optional<String> paramDescription;
            private final Optional<Consumer<ContentBuilder<?>>> defaultValue;
            private final String getterName;
            private final String name;
            private final String setterName;
            private final TypeName declaredType;

            /**
             * Create an instance providing a builder.
             *
             * @param builder extending builder base of this prototype
             */
            protected OptionInfoImpl(BuilderBase<?, ?> builder) {
                this.interfaceMethod = builder.interfaceMethod().map(Function.identity());
                this.declaringType = builder.declaringType().map(Function.identity());
                this.name = builder.name().get();
                this.getterName = builder.getterName().get();
                this.setterName = builder.setterName().get();
                this.declaredType = builder.declaredType().get();
                this.decorator = builder.decorator().map(Function.identity());
                this.includeInToString = builder.includeInToString();
                this.includeInEqualsAndHashCode = builder.includeInEqualsAndHashCode();
                this.confidential = builder.confidential();
                this.registryService = builder.registryService();
                this.sameGeneric = builder.sameGeneric();
                this.required = builder.required();
                this.builderOptionOnly = builder.builderOptionOnly();
                this.qualifiers = List.copyOf(builder.qualifiers());
                this.allowedValues = List.copyOf(builder.allowedValues());
                this.defaultValue = builder.defaultValue().map(Function.identity());
                this.configured = builder.configured().map(Function.identity());
                this.deprecation = builder.deprecation().map(Function.identity());
                this.provider = builder.provider().map(Function.identity());
                this.singular = builder.singular().map(Function.identity());
                this.accessModifier = builder.accessModifier();
                this.builderInfo = builder.builderInfo().map(Function.identity());
                this.runtimeType = builder.runtimeType().map(Function.identity());
                this.description = builder.description().map(Function.identity());
                this.paramDescription = builder.paramDescription().map(Function.identity());
                this.annotations = List.copyOf(builder.annotations());
                this.inheritedAnnotations = List.copyOf(builder.inheritedAnnotations());
            }

            @Override
            public Optional<TypedElementInfo> interfaceMethod() {
                return interfaceMethod;
            }

            @Override
            public Optional<TypeInfo> declaringType() {
                return declaringType;
            }

            @Override
            public String name() {
                return name;
            }

            @Override
            public String getterName() {
                return getterName;
            }

            @Override
            public String setterName() {
                return setterName;
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
            public boolean builderOptionOnly() {
                return builderOptionOnly;
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
            public Optional<RuntimeTypeInfo> runtimeType() {
                return runtimeType;
            }

            @Override
            public Optional<String> description() {
                return description;
            }

            @Override
            public Optional<String> paramDescription() {
                return paramDescription;
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
                        + "interfaceMethod=" + interfaceMethod + ","
                        + "declaringType=" + declaringType + ","
                        + "name=" + name + ","
                        + "getterName=" + getterName + ","
                        + "setterName=" + setterName + ","
                        + "declaredType=" + declaredType + ","
                        + "decorator=" + decorator + ","
                        + "includeInToString=" + includeInToString + ","
                        + "includeInEqualsAndHashCode=" + includeInEqualsAndHashCode + ","
                        + "confidential=" + confidential + ","
                        + "registryService=" + registryService + ","
                        + "sameGeneric=" + sameGeneric + ","
                        + "required=" + required + ","
                        + "builderOptionOnly=" + builderOptionOnly + ","
                        + "qualifiers=" + qualifiers + ","
                        + "allowedValues=" + allowedValues + ","
                        + "defaultValue=" + defaultValue + ","
                        + "configured=" + configured + ","
                        + "deprecation=" + deprecation + ","
                        + "provider=" + provider + ","
                        + "singular=" + singular + ","
                        + "accessModifier=" + accessModifier + ","
                        + "builderInfo=" + builderInfo + ","
                        + "runtimeType=" + runtimeType + ","
                        + "description=" + description + ","
                        + "paramDescription=" + paramDescription + ","
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
                return Objects.equals(interfaceMethod, other.interfaceMethod())
                        && Objects.equals(declaringType, other.declaringType())
                        && Objects.equals(name, other.name())
                        && Objects.equals(getterName, other.getterName())
                        && Objects.equals(setterName, other.setterName())
                        && Objects.equals(declaredType, other.declaredType())
                        && Objects.equals(decorator, other.decorator())
                        && includeInToString == other.includeInToString()
                        && includeInEqualsAndHashCode == other.includeInEqualsAndHashCode()
                        && confidential == other.confidential()
                        && registryService == other.registryService()
                        && sameGeneric == other.sameGeneric()
                        && required == other.required()
                        && builderOptionOnly == other.builderOptionOnly()
                        && Objects.equals(qualifiers, other.qualifiers())
                        && Objects.equals(allowedValues, other.allowedValues())
                        && Objects.equals(defaultValue, other.defaultValue())
                        && Objects.equals(configured, other.configured())
                        && Objects.equals(deprecation, other.deprecation())
                        && Objects.equals(provider, other.provider())
                        && Objects.equals(singular, other.singular())
                        && Objects.equals(accessModifier, other.accessModifier())
                        && Objects.equals(builderInfo, other.builderInfo())
                        && Objects.equals(runtimeType, other.runtimeType())
                        && Objects.equals(description, other.description())
                        && Objects.equals(paramDescription, other.paramDescription())
                        && Objects.equals(annotations, other.annotations())
                        && Objects.equals(inheritedAnnotations, other.inheritedAnnotations());
            }

            @Override
            public int hashCode() {
                return Objects.hash(interfaceMethod,
                                    declaringType,
                                    name,
                                    getterName,
                                    setterName,
                                    declaredType,
                                    decorator,
                                    includeInToString,
                                    includeInEqualsAndHashCode,
                                    confidential,
                                    registryService,
                                    sameGeneric,
                                    required,
                                    builderOptionOnly,
                                    qualifiers,
                                    allowedValues,
                                    defaultValue,
                                    configured,
                                    deprecation,
                                    provider,
                                    singular,
                                    accessModifier,
                                    builderInfo,
                                    runtimeType,
                                    description,
                                    paramDescription,
                                    annotations,
                                    inheritedAnnotations);
            }

        }

    }

    /**
     * Fluent API builder for {@link io.helidon.builder.codegen.OptionInfo}.
     */
    class Builder extends BuilderBase<Builder, OptionInfo> implements io.helidon.common.Builder<Builder, OptionInfo> {

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
