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
import java.util.function.Consumer;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.codegen.classmodel.ContentBuilder;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.Annotated;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypedElementInfo;

/**
 * Model of a prototype/builder option.
 */
@Prototype.Blueprint(detach = true)
interface OptionInfoBlueprint extends Annotated {
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
    @Option.DefaultBoolean(true)
    boolean includeInToString();

    /**
     * Whether to include this option in generated {@link java.lang.Object#equals(Object)}
     * and {@link java.lang.Object#hashCode()} methods.
     *
     * @return whether to include in the {@code equals} and {@code hashCode} methods
     */
    @Option.DefaultBoolean(true)
    boolean includeInEqualsAndHashCode();

    /**
     * Whether this option is confidential (i.e. we should not print the value in {@code toString} method).
     *
     * @return whether this option is confidential
     */
    @Option.DefaultBoolean(false)
    boolean confidential();

    /**
     * Whether this option should be loaded from {@code ServiceRegistry}.
     *
     * @return whether this option should be loaded from {@code ServiceRegistry}
     */
    @Option.DefaultBoolean(false)
    boolean registryService();

    /**
     * Whether this {@link java.util.Map} option is expected to have the same generic type for key and value.
     * For example, for a {@code Map<Class<?>, Instance<?>>} that has {@code sameGeneric} set to {@code true}, we would
     * generate singular method with signature {@code <T> put(Class<T>, Instance<T>}.
     *
     * @return whether a map has the same generic in key and value for a single mapping
     */
    @Option.DefaultBoolean(false)
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
    @Option.Singular
    List<Annotation> qualifiers();

    /**
     * List of allowed values for this option.
     *
     * @return allowed values
     */
    @Option.Singular
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
    @Option.Default("PUBLIC")
    AccessModifier accessModifier();

    /**
     * If the option has a builder, return its information.
     *
     * @return builder information, if present
     */
    Optional<OptionBuilder> builderInfo();
}
