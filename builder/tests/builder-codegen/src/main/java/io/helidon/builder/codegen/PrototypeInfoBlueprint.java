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

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.codegen.classmodel.Javadoc;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.Annotated;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;

/**
 * Information about the prototype we are going to build.
 */
@Prototype.Blueprint(detach = true)
interface PrototypeInfoBlueprint extends Annotated {
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
     * <ul>
     * <li>Check the method signature (i.e. {@code process(java.lang.String)}, if accepted, use it as an option</li>
     * <li>Check the method name (i.e. {@code process}, if accepted, use it as an option</li>
     * <li>Otherwise the default method will not be an option</li>
     * </ul>
     *
     * @return predicate for method names
     */
    @Option.DefaultCode("it -> false")
    Predicate<String> defaultMethodsPredicate();

    /**
     * Access modifier for the generated prototype.
     *
     * @return access modifier, defaults to {@code public}
     */
    @Option.Default("PUBLIC")
    AccessModifier accessModifier();

    /**
     * Access modifier for the generated builder.
     *
     * @return access modifier, defaults to {@code public}
     */
    @Option.Default("PUBLIC")
    AccessModifier builderAccessModifier();

    /**
     * Whether to create an empty {@code create()} method.
     *
     * @return whether to create an empty {@code create()} method, defaults to {@code true}
     */
    @Option.DefaultBoolean(true)
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
    @Option.DefaultBoolean(true)
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
    @Option.DefaultBoolean(false)
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
    @Option.Singular
    Set<TypeName> superTypes();

    /**
     * Types the generated prototype should provide, if this prototype is/configures a service provider.
     *
     * @return provider provides types
     */
    @Option.Singular
    Set<TypeName> providerProvides();

    /**
     * Constants to be defined on the prototype.
     * A constant may be either a reference to another constant or a generated value.
     *
     * @return constants to add to the prototype
     */
    @Option.Singular
    List<PrototypeConstant> constants();

    /**
     * Additional methods to be added to the prototype as default methods.
     * <p>
     * Non-default interface methods cannot be added, as the implementation is not customizable.
     * This list does NOT contain option methods.
     *
     * @return custom methods to add to the prototype
     */
    @Option.Singular
    List<GeneratedMethod> prototypeMethods();

    /**
     * Additional methods to be added to the prototype builder base.
     * It is your responsibility to ensure these methods do not conflict with option methods.
     * This list does NOT contain option methods.
     *
     * @return custom methods to add to the prototype builder base
     */
    @Option.Singular
    List<GeneratedMethod> builderMethods();

    /**
     * Static factory methods to be added to the prototype, or runtime type factory methods.
     * <p>
     * This method exists only for backwards compatibility and will be removed in a future major version.
     *
     * @return a list of factory methods declared on the blueprint or a reference custom methods type
     * @deprecated use {@link #prototypeFactories()}, or {@link #runtimeTypeFactories()}, or
     *         {@link #configFactories()} instead, only present for backwards compatibility
     */
    @Deprecated(forRemoval = true, since = "4.4.0")
    List<DeprecatedFactoryMethod> deprecatedFactoryMethods();

    /**
     * Static factory methods to be added to the prototype.
     *
     * @return a list of factory methods to add to the prototype
     */
    @Option.Singular("prototypeFactory")
    List<GeneratedMethod> prototypeFactories();

    /**
     * Factory methods to be used when mapping config to types.
     * These methods will never be made public.
     * <p>
     * Config factory methods may exist for a specific option, or for any option that matches the type.
     *
     * @return factory methods to use when mapping config to types
     */
    @Option.Singular("configFactory")
    List<FactoryMethod> configFactories();

    /**
     * Factory methods to create runtime types from a builder.
     * If a method is available for a specific option and matches its types, a setter with a parameter of
     * consumer of the builder type will be added to the builder base.
     * <p>
     * Runtime factory methods may exist for a specific option, or for any option that matches the type.
     *
     * @return factory methods to create runtime types from a builder
     */
    @Option.Singular("runtimeTypeFactory")
    List<RuntimeTypeInfo> runtimeTypeFactories();
}
