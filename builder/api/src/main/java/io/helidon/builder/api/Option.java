/*
 * Copyright (c) 2023, 2026 Oracle and/or its affiliates.
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

package io.helidon.builder.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import io.helidon.common.Api;

/**
 * Prototype option annotations.
 */
public final class Option {
    private Option() {
    }

    /**
     * Mark a prototype option as one that can be read from {@code io.helidon.config.Config}.
     */
    @Target(ElementType.METHOD)
    @Inherited
    @Retention(RetentionPolicy.CLASS)
    public @interface Configured {
        /**
         * Override "guessed" configuration key.
         * The configuration key is the method name converted to snake case by default.
         *
         * @return custom configuration key
         */
        String value() default "";

        /**
         * If set to {@code true}, the nested configurable object will not have its own config key,
         * but will use the config of the current configurable object.
         *
         * @return whether to merge the nested object into this object
         */
        boolean merge() default false;
    }

    /**
     * Customize access modifier for builder methods.
     * If undefined on a method, the builder method will be {@code public}.
     * This changes the modifier of the builder methods, as getters are always public (as inherited
     * from the blueprint).
     * <p>
     * Useful modifiers:
     * <ul>
     *     <li>empty - (package private) the builder method is to be used only from
     *              configuration, custom builder decorator, or other types in the same package</li>
     *     <li>{@code private} - the builder method is to be used only from
     *                   configuration</li>
     * </ul>
     */
    @Target(ElementType.METHOD)
    @Inherited
    @Retention(RetentionPolicy.CLASS)
    public @interface Access {
        /**
         * Custom access modifier to use.
         *
         * @return modifier to use
         */
        String value();
    }

    /**
     * Mark option as a required option.
     * <p>
     * Required options of primitive types must be configured through the builder. The default value of the primitive type
     * is ignored.
     * This option is not applicable when combined with the default value annotations, as such fields always have values,
     * unless the type is {@link java.util.Optional}
     */
    @Target(ElementType.METHOD)
    @Inherited
    @Retention(RetentionPolicy.CLASS)
    public @interface Required {
    }

    /**
     * Mark option as sourced from services discovered by a {@link java.util.ServiceLoader}, or by Helidon Service
     * Registry when {@link io.helidon.builder.api.Prototype.RegistrySupport} is enabled.
     * Use if the configuration may be provided by another module not known to us.
     * <p>
     * When the option is also {@link io.helidon.builder.api.Option.Configured}, discovered configured providers are
     * used to create services from configuration.
     * <p>
     * A configured provider option uses the option's configured key as the provider configuration node. For example,
     * if the provider option key is {@code my-providers}, provider entries are read from {@code my-providers}. There
     * is no extra nested {@code providers} key unless the option itself is configured with that key.
     * <p>
     * To control whether to discover services or not for a configured provider option, you can specify a sibling
     * boolean key named {@code <option-config-key>-discover-services}, such as
     * {@code my-providers-discover-services}. This is aligned with the generated methods on the builder and overrides
     * {@link #discoverServices()} defined on this annotation.
     * <p>
     * The configuration key rules are the same regardless whether the method returns a single value, a single
     * {@link java.util.Optional} value, a list of values, or an optional list of values. A single value or optional
     * value must have at most one configured provider entry.
     * <p>
     * When using an optional list, an absent configuration node does not by itself set the optional value; the value
     * stays empty unless builder values or service discovery provide entries. An explicitly empty list or object
     * configuration node produces {@code Optional.of(List.of())}.
     * <p>
     * The following examples first use the compatibility defaults, {@link Provider.Identity#TYPE_AND_NAME
     * TYPE_AND_NAME} with {@link Provider.ConfigForm#AUTO AUTO}. With those defaults, either object or list form is
     * accepted. Option {@code myProvider} returning one value, or an {@link java.util.Optional} value, can use object
     * form:
     * <pre>
     * my-type:
     *   my-provider:
     *     provider-id: # object key supplies the instance name
     *       type: provider-type
     *       provider-key1: "providerValue"
     *       provider-key2: "providerValue"
     * </pre>
     * <p>
     * Option {@code myProviders} returning a Java {@link java.util.List} can use the same object form; the Java return
     * type does not require list-form configuration. This example configures two instances of the same provider type:
     * <pre>
     * my-type:
     *   my-providers-discover-services: true # default of this value is controlled by annotation
     *   my-providers:
     *     first-provider:
     *       type: provider-type
     *       provider-key1: "providerValue"
     *       provider-key2: "providerValue"
     *     second-provider:
     *       type: provider-type
     *       provider-key1: "anotherValue"
     * </pre>
     * The same option can instead use list form with the compatibility defaults:
     * <pre>
     * my-type:
     *   my-providers:
     *     - type: provider-type
     *       name: first-provider
     *       provider-key1: "providerValue"
     *       provider-key2: "providerValue"
     *     - type: provider-type
     *       name: second-provider
     *       provider-key1: "anotherValue"
     * </pre>
     * In object form, each object key supplies an instance name and those names are therefore globally unique within
     * the object. Object form does not define the order in which values are materialized. List form preserves the
     * configured order, may reuse a name for a different provider type, and rejects an exact duplicate
     * {@code (type, name)} pair. Object form is therefore intentionally less expressive than list form for
     * {@link Provider.Identity#TYPE_AND_NAME TYPE_AND_NAME}.
     */
    @Target(ElementType.METHOD)
    @Inherited
    @Retention(RetentionPolicy.CLASS)
    public @interface Provider {
        /**
         * Provider contract used to discover implementations for this option.
         * <p>
         * For a configured option, implementations of this contract are configured providers that create the option's
         * values from configuration. For a non-configured option, implementations of this contract are themselves the
         * option values. The configured-provider identity and configuration-form properties do not apply to a
         * non-configured option.
         *
         * @return provider contract used for discovery
         */
        Class<?> value();

        /**
         * Whether service discovery may add option values that do not have corresponding configuration.
         * <p>
         * For a configured provider option, provider implementations are always discovered so they can create entries
         * that are present in configuration. A value of {@code false} only prevents those providers from adding
         * unconfigured default values; it does not disable the discovery needed to create configured entries. For a
         * non-configured provider option, {@code false} prevents discovered implementations from being added as option
         * values.
         * <p>
         * Discovery uses the service loader, or Helidon Service Registry when
         * {@link io.helidon.builder.api.Prototype.RegistrySupport} is enabled.
         * For configured provider options, this can be overridden by a sibling configuration option named
         * {@code <option-config-key>-discover-services}. For a non-configured provider option, this value initializes
         * the generated discovery flag, which can still be changed using the generated builder method. The default is
         * {@code true}.
         *
         * @return whether service discovery may add values without matching configuration by default
         */
        boolean discoverServices() default true;

        /**
         * Values that constitute the identity of each configured provider instance.
         * <p>
         * Identity is independent of {@link #configForm() configuration syntax}. It defines which values distinguish
         * configured instances, not whether the outer configuration is written as an object or a list. For example,
         * {@link Identity#TYPE_ONLY TYPE_ONLY} can be combined with {@link ConfigForm#LIST LIST}, and
         * {@link Identity#TYPE_AND_NAME TYPE_AND_NAME} can be combined with {@link ConfigForm#OBJECT OBJECT}.
         * <p>
         * With {@link Identity#TYPE_AND_NAME TYPE_AND_NAME}, an object key supplies the instance name, so object-form
         * names are globally unique. List form may reuse a name for a different provider type, but an exact duplicate
         * {@code (type, name)} pair is invalid. The default is {@link Identity#TYPE_AND_NAME TYPE_AND_NAME}. This
         * property is ignored when the provider option is not also
         * {@link io.helidon.builder.api.Option.Configured configured}.
         *
         * @return configured provider identity model
         */
        @Api.Incubating
        Identity identity() default Identity.TYPE_AND_NAME;

        /**
         * Outer configuration container accepted for configured provider entries.
         * <p>
         * The form is independent of {@link #identity() provider identity}. It controls only whether the configured
         * provider collection is an object, a list, or either. It is also independent of the option's Java type: a Java
         * {@link java.util.List} option may use object-form configuration. The selected form does not constrain the
         * provider-specific value within each entry beyond the metadata required by the selected identity model.
         * <p>
         * {@link ConfigForm#AUTO AUTO} preserves compatibility by resolving to {@link ConfigForm#OBJECT OBJECT} for
         * {@link Identity#TYPE_ONLY TYPE_ONLY}, and to {@link ConfigForm#OBJECT_OR_LIST OBJECT_OR_LIST} for
         * {@link Identity#TYPE_AND_NAME TYPE_AND_NAME}. Object form has unspecified materialization order; list form
         * preserves configured order. An explicit form overrides the automatic choice. The default is
         * {@link ConfigForm#AUTO AUTO}. This property is ignored when the provider option is not also
         * {@link io.helidon.builder.api.Option.Configured configured}.
         *
         * @return accepted outer configuration form
         */
        @Api.Incubating
        ConfigForm configForm() default ConfigForm.AUTO;

        /**
         * Identity model for configured provider instances.
         * <p>
         * Identity and {@link ConfigForm configuration form} are separate axes. Neither enum value selects object or
         * list syntax by itself when an explicit form is configured.
         */
        @Api.Incubating
        enum Identity {
            /**
             * Use provider type as the complete configured-instance identity.
             * <p>
             * At most one configured instance may exist for each provider type. In object form, the object key supplies
             * the provider type; nested {@code type} and {@code name} metadata properties are forbidden. In list form,
             * each entry must declare {@code type}, must not declare {@code name}, and provider types must be unique.
             * The two forms represent the same set of unique provider types. Object form has unspecified
             * materialization order, while list form preserves configured order.
             * <p>
             * This value selects identity only; it does not require object syntax. Use
             * {@link ConfigForm#LIST LIST} or {@link ConfigForm#OBJECT_OR_LIST OBJECT_OR_LIST} when list syntax is
             * intentionally supported. Provider-specific content within an entry retains the shape accepted by that
             * provider.
             */
            TYPE_ONLY,

            /**
             * Use provider type and instance name together as the configured-instance identity.
             * <p>
             * In object form, the object key supplies the name and nested {@code type} selects the provider, defaulting
             * to the object key when omitted. Object keys make names globally unique, and object form has unspecified
             * materialization order. In list form, each entry declares {@code type}; {@code name} defaults to
             * {@code type} when omitted. A list may reuse a name for a different provider type, while an exact duplicate
             * {@code (type, name)} pair is invalid and causes configuration to fail. List form preserves configured
             * order. Object form is intentionally less expressive because it cannot reuse a name across provider
             * types. Both forms allow multiple differently named instances of the same provider type. The legacy
             * single-key nested list-entry syntax remains accepted.
             * <p>
             * This value selects identity only; it does not enable list syntax. An explicit
             * {@link ConfigForm#OBJECT OBJECT} requires an object, while {@link ConfigForm#LIST LIST} requires a list.
             * Provider-specific content within an entry retains the shape accepted by that provider.
             */
            TYPE_AND_NAME
        }

        /**
         * Accepted outer container for configured provider entries.
         * <p>
         * This enum controls syntax only. It does not select the configured provider identity model and does not impose
         * a generic shape on provider-specific values stored within an entry.
         */
        @Api.Incubating
        enum ConfigForm {
            /**
             * Select the outer configuration form from {@link Provider#identity() identity}.
             * <p>
             * {@link Identity#TYPE_ONLY TYPE_ONLY}
             * resolves to {@link #OBJECT OBJECT}, while {@link Identity#TYPE_AND_NAME TYPE_AND_NAME} resolves to
             * {@link #OBJECT_OR_LIST OBJECT_OR_LIST}.
             * <p>
             * Together with the default {@link Identity#TYPE_AND_NAME TYPE_AND_NAME} identity, this preserves the
             * previous behavior that accepts either configured-provider object or list syntax. The form selected by
             * this value controls only the outer container and its ordering semantics; it does not constrain
             * provider-specific content within an entry.
             */
            AUTO,

            /**
             * Require the outer configured-provider container to be an object.
             * <p>
             * With {@link Identity#TYPE_ONLY TYPE_ONLY}, each object key is the provider type and nested {@code type}
             * and {@code name} metadata is forbidden. With {@link Identity#TYPE_AND_NAME TYPE_AND_NAME}, the object key
             * is the globally unique name and nested {@code type} selects the provider, defaulting to the object key.
             * Differently named instances of the same type are supported. Object entries have unspecified
             * materialization order.
             * <p>
             * This value selects syntax only. It does not select an identity model or constrain the shape of
             * provider-specific content within an entry.
             */
            OBJECT,

            /**
             * Require the outer configured-provider container to be a list.
             * <p>
             * With {@link Identity#TYPE_ONLY TYPE_ONLY}, every list entry requires {@code type}, forbids {@code name},
             * and types must be unique. With {@link Identity#TYPE_AND_NAME TYPE_AND_NAME}, {@code name} defaults to
             * {@code type}, distinct names distinguish entries of one type, and the same name may be reused for another
             * provider type. An exact duplicate {@code (type, name)} pair is invalid and causes configuration to fail;
             * the legacy single-key nested entry syntax is also accepted. Entries are materialized in configured list
             * order.
             * <p>
             * This value selects syntax only. It does not select an identity model or constrain provider-specific
             * content beyond the metadata needed by list syntax.
             */
            LIST,

            /**
             * Accept either an object or a list as the outer configured-provider container.
             * <p>
             * Each entry is interpreted using the corresponding {@link #OBJECT OBJECT} or {@link #LIST LIST} rules and
             * the identity selected by {@link Provider#identity()}. For
             * {@link Identity#TYPE_AND_NAME TYPE_AND_NAME}, object keys supply globally unique names, while list form
             * may reuse a name for different provider types and rejects an exact duplicate {@code (type, name)} pair.
             * Both forms support multiple differently named instances of one type. Object form has unspecified
             * materialization order; list form preserves configured order.
             * <p>
             * This value selects syntax only. It does not add name identity to
             * {@link Identity#TYPE_ONLY TYPE_ONLY}, remove name identity from
             * {@link Identity#TYPE_AND_NAME TYPE_AND_NAME}, or constrain provider-specific content within an entry.
             */
            OBJECT_OR_LIST
        }
    }

    /**
     * Options annotated with this annotation will use service registry to discover instances to use.
     * This annotation cannot be combined with {@link io.helidon.builder.api.Option.Configured} - if you want
     * providers configured from configuration, kindly use {@link io.helidon.builder.api.Option.Provider}.
     * <p>
     * Behavior depends on the return type of the annotated method:
     * <ul>
     *     <li>A single instance - if the instance is configured on the builder by hand, registry is not used</li>
     *     <li>An {@link java.util.Optional} instance - ditto</li>
     *     <li>A {@link java.util.List} of instances - instances configured on the builder are combined with instances
     *      discovered in the registry; there is a generated method that allows for disabling registry use for each
     *      service</li>
     * </ul>
     *
     * Options annotated with this annotation will load the instances as the default value (before method {@code builder()})
     * returns, thus you have full control over the field, be it an Optional, single value, or a List.
     * <p>
     * To support usage of custom service registry, a {@code builder(ServiceRegistry)} method will be generated as well.
     */
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.CLASS)
    public @interface RegistryService {
    }

    /**
     * Allowed values for this option.
     * The allowed value is always configured as a string, and is compared to {@link java.lang.String#valueOf(Object)} of the
     * value.
     */
    @Target(ElementType.METHOD)
    @Inherited
    @Retention(RetentionPolicy.CLASS)
    public @interface AllowedValues {
        /**
         * All allowed values for this prototype option.
         *
         * @return values
         */
        AllowedValue[] value();
    }

    /**
     * Can be used to define a list of possible values of an option.
     */
    @Target(ElementType.METHOD)
    @Inherited
    @Retention(RetentionPolicy.CLASS)
    @Repeatable(AllowedValues.class)
    public @interface AllowedValue {
        /**
         * Value of the option.
         *
         * @return value
         */
        String value();

        /**
         * Description of this value, used in documentation, may be used in error handling.
         *
         * @return description
         */
        String description();
    }

    /**
     * A String default value for a prototype option.
     */
    @Target(ElementType.METHOD)
    @Inherited
    @Retention(RetentionPolicy.CLASS)
    public @interface Default {
        /**
         * Default value(s) to use.
         *
         * @return prototype option default value (String)
         */
        String[] value();
    }

    /**
     * An integer default value for a prototype option.
     */
    @Target(ElementType.METHOD)
    @Inherited
    @Retention(RetentionPolicy.CLASS)
    public @interface DefaultInt {
        /**
         * Default value(s) to use.
         *
         * @return prototype option default value (String)
         */
        int[] value();
    }

    /**
     * A long default value for a prototype option.
     */
    @Target(ElementType.METHOD)
    @Inherited
    @Retention(RetentionPolicy.CLASS)
    public @interface DefaultLong {
        /**
         * Default value(s) to use.
         *
         * @return prototype option default value (String)
         */
        long[] value();
    }

    /**
     * A double default value for a prototype option.
     */
    @Target(ElementType.METHOD)
    @Inherited
    @Retention(RetentionPolicy.CLASS)
    public @interface DefaultDouble {
        /**
         * Default value(s) to use.
         *
         * @return prototype option default value (String)
         */
        double[] value();
    }

    /**
     * A boolean default value for a prototype option.
     */
    @Target(ElementType.METHOD)
    @Inherited
    @Retention(RetentionPolicy.CLASS)
    public @interface DefaultBoolean {
        /**
         * Default value(s) to use.
         *
         * @return prototype option default value (String)
         */
        boolean[] value();
    }

    /**
     * A default value created from a method for a prototype option.
     */
    @Target(ElementType.METHOD)
    @Inherited
    @Retention(RetentionPolicy.CLASS)
    public @interface DefaultMethod {
        /**
         * Type to invoke the method on, defaults to the type of the property.
         *
         * @return type that contains the static default method to use
         */
        Class<?> type() default DefaultMethod.class;

        /**
         * Static method without parameters that provides the type of the property, declared on
         * {@link #type()}. The method is expected to return a single value, {@link java.util.List} of values,
         * or a {@link java.util.Set} of values for all types except for {@link java.util.Map}, where the
         * default value should return a {@link java.util.Map}.
         *
         * @return name of a static method without parameters to use
         */
        String value();
    }

    /**
     * A default value that will be copied verbatim into the sources.
     */
    @Target(ElementType.METHOD)
    @Inherited
    @Retention(RetentionPolicy.CLASS)
    public @interface DefaultCode {
        /**
         * String defining the default value. If types are used from outside of package, they should be fully qualified.
         * Such types can be surrounded with {@code @} to give our class model a chance to correctly optimize imports.
         * The code is expected to be a single line only, not ending with semicolon.
         *
         * @return source code to generate default value, must return correct type of the field
         */
        String value();
    }

    /**
     * Applying this annotation to a {@link io.helidon.builder.api.Prototype.Blueprint}-annotated interface method will cause
     * the generated class to also include additional "add*()" methods. This will only apply, however, if the method is for
     * a {@link java.util.Map}, {@link java.util.List}, or {@link java.util.Set}.
     */
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.CLASS)
    public @interface Singular {

        /**
         * The optional value specified here will determine the singular form of the method name.
         * For instance, if we take a method like this:
         * <pre>{@code
         * @Singlular("pickle")
         * List<Pickle> getPickles();
         * }</pre>
         * an additional generated method named {@code addPickle(Pickle val)} will be placed on the builder of the generated
         * class.
         * <p>This annotation only applies to getter methods that return a Map, List, or Set. If left undefined then the add
         * method
         * will use the default method name, dropping any "s" that might be present at the end of the method name (e.g.,
         * pickles -> pickle).
         *
         * @return The singular name to add
         */
        String value() default "";

        /**
         * When set to {@code true}, the prefix {@code add} or {@code put} will be added to generated methods
         * (for collections and maps respectively).
         * When set to {@code false}, the {@link #value()} will be used as a full method name for singular add/put methods.
         * <p>
         * In case you set this to {@code false}, you must make sure the method name does not conflict with other methods
         * on the generated type
         *
         * @return whether to add prefix to the generated method, defaults to {@code true}
         */
        boolean withPrefix() default true;
    }

    /**
     * Useful for marking map properties, where the key and value must have the
     * same generic type. For example you can declare a Map of a generalized type (such as Class)
     * to an Object. This would change the singular setters to ones that expect the types to be the same.
     * <p>
     * Example:
     * For {@code Map<Class<Object>, Object>}, the following method would be generated:
     * {@code <TYPE> BUILDER put(Class<TYPE> key, TYPE value)}.
     * <p>
     * This annotation is only allowed on maps that have a key with a single type parameter,
     * and value of Object, or with a single type parameter.
     */
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.CLASS)
    public @interface SameGeneric {
    }

    /**
     * Mark a getter method as redundant - not important for {@code equals}, {@code hashcode}, and/or {@code toString}.
     * The generated prototype will ignore the fields for these methods in equals and hashcode methods.
     * All other fields will be included.
     * <p>
     * In case both properties are set to false, it is af this annotation is not present.
     */
    @Target(ElementType.METHOD)
    // note: class retention needed for cases when derived builders are inherited across modules
    @Retention(RetentionPolicy.CLASS)
    public @interface Redundant {
        /**
         * Set to {@code false} to mark this NOT redundant for equals and hashcode.
         *
         * @return whether this should be ignored for equals and hashcode
         */
        boolean equality() default true;

        /**
         * Set to {@code false} to mark this NOT redundant for toString.
         *
         * @return whether this should be ignored for toString
         */
        boolean stringValue() default true;
    }

    /**
     * Mark a getter method as confidential - not suitable to be used in clear text in {@code toString} method.
     * The field will be mentioned (whether it has a value or not), but its value will not be visible.
     */
    @Target(ElementType.METHOD)
    // note: class retention needed for cases when derived builders are inherited across modules
    @Retention(RetentionPolicy.CLASS)
    public @interface Confidential {
    }

    /**
     * Mark an option as deprecated.
     * This will introduce {@link java.lang.Deprecated} annotation on all related methods.
     * Since and if for removal will be taken from the {@link java.lang.Deprecated} annotation on this method.
     * This annotation is an extension to the Java annotation. If not defined, description from javadoc tag {@code deprecated}
     * will be used for all setters and getters instead.
     */
    @Target(ElementType.METHOD)
    // note: class retention needed for cases when derived builders are inherited across modules
    @Retention(RetentionPolicy.CLASS)
    public @interface Deprecated {
        /**
         * Alternative option that replaces this option.
         *
         * @return name of the method that should be used instead
         */
        String value();
    }


    /**
     * Explicitly define a type (may include generics) in case the type is located
     * in the same module, and cannot be inferred correctly by the annotation processor.
     * This is always needed for types with generics in the same module.
     */
    @Target(ElementType.METHOD)
    // note: class retention needed for cases when derived builders are inherited across modules
    @Retention(RetentionPolicy.CLASS)
    public @interface Type {
        /**
         * Type declaration including generic types (must match the declared generic type on the blueprint).
         *
         * @return type name with generic declaration
         */
        String value();
    }

    /**
     * Define an option decorator.
     * This is useful for example when setting a compound option, where we need to set additional options on this builder.
     * <p>
     * Decorator on {@link java.util.List} option will have the
     * {@link io.helidon.builder.api.Prototype.OptionDecorator#decorate(Object, Object)} called for singular values,
     * and {@link io.helidon.builder.api.Prototype.OptionDecorator#decorateSetList(Object, java.util.List)}
     * called for setter of the list, and
     * {@link io.helidon.builder.api.Prototype.OptionDecorator#decorateAddList(Object, java.util.List)}
     * called for additive setter of the list.
     * <p>
     * Similar approach is taken when decorating a {@link java.util.Set} option.
     * <p>
     * Decorator on {@link java.util.Map} based options will be ignored.
     * <p>
     * Decorator on optional values must accept an optional (as it would be called both from the setter and unset methods).
     */
    @Target(ElementType.METHOD)
    // note: class retention needed for cases when derived builders are inherited across modules
    @Retention(RetentionPolicy.CLASS)
    public @interface Decorator {
        /**
         * Type declaration including generic types (must match the declared generic type on the blueprint).
         *
         * @return type name with generic declaration
         */
        Class<? extends Prototype.OptionDecorator<?, ?>> value();
    }

    /**
     * Definition of how {@link java.util.Map} keys and values should be constructed.
     * <p>
     * If this annotation is not used, traversed is automatically applied on String and primitive/boxed types.
     * In all other cases, non-traverse approach is applied.
     * <p>
     * If this annotation is used, it will use the {@code io.helidon.config.Config#traverse} method
     * to perform a depth-first traversal of the node and its subtrees.
     * Note: this annotation takes effect only when used in combination with {@link Configured}.
     * <p>
     * For example:
     * <pre>{@code
     * test-map:
     *    key: "test-value1"
     *    test-key:
     *       second-part: "test-value2"
     *       third-part: "test-value3"
     * }</pre>
     * <p>
     * Will be handled as:
     * <pre>{@code
     * key: "key" value: "test-value1"
     * key: "test-key.second-part" value: "test-value2"
     * key: "test-key.third-part" value: "test-value3"
     * }</pre>
     */
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.CLASS)
    public @interface TraverseConfig {

        /**
         * Whether to use traverse method to handle map key and value.
         *
         * @return true to enable traverse and false to disable
         */
        boolean value() default true;

    }

    /**
     * An option may return a type that is not under our control, yet we have a prototype that can be used to
     * create it. Such a prototype can be referenced through this annotation, so we use it for builder consumer,
     * to add a builder method with it as a parameter, and to use it in {@code config} method if it is configured.
     * <p>
     * IMPORTANT: the referenced prototype {@code build} method must return the type returned by this option
     */
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.CLASS)
    public @interface PrototypedBy {
        /**
         * Type of the prototype that is a factory of the type returned by the annotated method.
         * If the type is in the same package as the type of the annotated method, class name is sufficient, otherwise
         * a fully qualified name must be used.
         *
         * @return prototype class that builds the type of the annotated option
         */
        String value();
    }
}
