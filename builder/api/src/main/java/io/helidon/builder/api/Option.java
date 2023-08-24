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

package io.helidon.builder.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Prototype option annotations.
 */
public final class Option {
    private Option() {
    }

    /**
     * Mark a prototype option as one that can be read from {@link io.helidon.common.config.Config}.
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
    }

    /**
     * Customize access modifier. Defaults to {@code public}.
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
     * When used on a primitive type, the value MUST be configured through the builder (e.g. default value
     * of the primitive type is ignored).
     * When combined with and of the default value annotations, this option is useless, as there always is
     * a value.
     */
    @Target(ElementType.METHOD)
    @Inherited
    @Retention(RetentionPolicy.CLASS)
    public @interface Required {
    }

    /**
     * Mark option as sourced from a {@link java.util.ServiceLoader}.
     * Use if the configuration may be provided by another module not know to us.
     * <p>
     * If the method returns a list, the provider configuration must be under config key {@code providers} under
     * the configured option. On the same level as {@code providers}, there can be {@code discover-services} boolean
     * defining whether to look for services from service loader even if not configured in the configuration (this would
     * override {@link #discoverServices()} defined on this annotation.
     * <p>
     * Option called {@code myProvider} that returns a single provider, or an {@link java.util.Optional} provider example
     * in configuration:
     * <pre>
     * my-type:
     *   my-provider:
     *     provider-id:
     *       provider-key1: "providerValue"
     *       provider-key2: "providerValue"
     * </pre>
     * <p>
     * Option called {@code myProviders} that returns a list of providers in configuration:
     * <pre>
     * my-type:
     *   my-providers:
     *     discover-services: true # default of this value is controlled by annotation
     *     providers:
     *       provider-id:
     *         provider-key1: "providerValue"
     *         provider-key2: "providerValue"
     *       provider2-id:
     *         provider2-key1: "provider2Value"
     * </pre>
     */
    @Target(ElementType.METHOD)
    @Inherited
    @Retention(RetentionPolicy.CLASS)
    public @interface Provider {
        /**
         * The service provider interface that is used to discover
         * implementations. The type of the property is the service provided by that provider.
         *
         * @return type of the provider.
         */
        Class<?> value();

        /**
         * Whether to discover all services using a service loader by default.
         * When set to true, all services discovered by the service loader will be added (even if no configuration
         * node exists for them). When se to false, only services that have a configuration node will be added.
         * This can be overridden by {@code discover-services} configuration option under this option's key.
         *
         * @return whether to discover services by default for a provider
         */
        boolean discoverServices() default true;
    }

    /**
     * Allowed values for this option.
     * The allowed value is always configured as a string, and it is compared to {@link java.lang.String#valueOf(Object)} of the
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
}
