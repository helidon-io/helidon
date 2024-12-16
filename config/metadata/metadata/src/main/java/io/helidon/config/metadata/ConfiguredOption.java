/*
 * Copyright (c) 2021, 2024 Oracle and/or its affiliates.
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

package io.helidon.config.metadata;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A single configuration option.
 * Must be used together with {@link io.helidon.config.metadata.Configured}.
 * Usually this should target a builder method that configures appropriate option.
 * If defined on a static method, it must be a {code static T create(X)} method, where
 * X is either {@code io.helidon.config.Config} or a single option.
 * <p>
 * There may be multiple {@code create(X)} methods defined on a single interface/class, each
 * of them annotated with a different list of options. In such a case the grouping
 * should be used as defined on these methods. If a method {@code create(Config)} exists,
 * it should use the same groups (e.g. if one method defines URI and Proxy, and another
 * method defines a Path, the {@code create(Config)} method can accept either (URI, proxy host, proxy port)
 * or (path).
 * <p>
 * By default, the name of the method is considered to be the configuration option used.
 * If method contains camel case, it will be changed to lower case hyphenated (this describes
 * the implementation of Helidon specific annotation processor and expectations).
 * <p>
 * Example - method {@code public Builder listenAddress(String)} would be configured
 * by key {@code listen-address}.
 */
@Target(ElementType.METHOD)
@Inherited
@Retention(RetentionPolicy.CLASS)
@Repeatable(ConfiguredOptions.class)
public @interface ConfiguredOption {
    /**
     * A string meaning that a value is not specified (to allow empty String as a meaningful value).
     */
    String UNCONFIGURED = "io.helidon.config.metadata.ConfiguredOption.UNCONFIGURED";

    /**
     * The default value for {@link #required()} is {@code false}.
     */
    boolean DEFAULT_REQUIRED = false;

    /**
     * The key of the configuration option as used in config.
     *
     * @return config key
     */
    String key() default "";

    /**
     * The type of the configuration option.
     * By default it is the type of the first parameter. If this annotation
     * exists on a class, type defaults to {@link java.lang.String}.
     *
     * @return type of the configuration option
     */
    Class<?> type() default ConfiguredOption.class;

    /**
     * Description of the configuration option.
     * By default javadoc of the builder method is used. If this annotation exists
     * on a class, description is mandatory.
     *
     * @return description of the configuration option
     */
    String description() default "";

    /**
     * Whether this option is truly required (e.g. the option must be present in configuration, otherwise
     * the component would fail). This MUST NOT be configured together with default value, as that would
     * make this option optional (as a default value exists).
     *
     * @return {@code true} for required option, {@code false} for options that are optional.
     *
     * @see #DEFAULT_REQUIRED
     */
    boolean required() default DEFAULT_REQUIRED;

    /**
     * Default value of this option if not configured explicitly.
     * @return default value
     */
    String value() default UNCONFIGURED;

    /**
     * Set to {@code true} for experimental configuration.
     *
     * @return whether this option is experimental
     */
    boolean experimental() default false;

    /**
     * Kind of this option.
     * Defaults to {@link Kind#VALUE},
     * autodetects {@link Kind#LIST} if the parameter is an actual {@link java.util.List}, {@link java.util.Set}
     * or {@link java.lang.Iterable}.
     * {@link Kind#MAP} is detected as well, though the type must be a String to a primitive or string
     *
     * @return kind of configuration option
     */
    Kind kind() default Kind.VALUE;

    /**
     * Set to true if the configuration may be provided by another module not know to us.
     * The provider must then be configured to {@link Configured#provides()} this type.
     * In addition, the {@link #providerType()} must be set to the correct service provider interface,
     * so code can be correctly generated.
     * <p>
     * If the method returns a list, the provider configuration must be under config key {@code providers} under
     * the configured option. On the same level as {@code providers}, there can be {@code discover-services} boolean
     * defining whether to look for services from service loader even if not configured in the configuration (this would
     * override {@link #providerDiscoverServices()} defined on this annotation.
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
     *
     * @return whether this requires a provider with configuration, defaults to {@code false}
     * @see #providerDiscoverServices()
     */
    boolean provider() default false;

    /**
     * If {@link #provider()}, this points to the provider interface that is used to discover
     * implementations.
     *
     * @return type of the provider.
     */
    Class<?> providerType() default ConfiguredOption.class;

    /**
     * Whether to discover all services using a service loader by default.
     * When set to true, all services discovered by the service loader will be added (even if no configuration
     * node exists for them). When se to false, only services that have a configuration node will be added.
     * This can be overridden by {@code discover-services} configuration option under this option's key.
     *
     * @return whether to discover services by default for a provider
     */
    boolean providerDiscoverServices() default true;

    /**
     * For options that have a predefined set of allowed values.
     *
     * @return allowed values
     */
    ConfiguredValue[] allowedValues() default {};

    /**
     * Configure to {@code true} if this option is deprecated.
     *
     * @return whether this configured option is deprecated
     */
    boolean deprecated() default false;

    /**
     * When set to {@code true}, this property will be part of the parent structure (e.g. the {@link #key()}
     * must be empty, and this must be a complex node).
     *
     * @return whether to merge the child nodes directly with parent node without a key
     */
    boolean mergeWithParent() default false;

    /**
     * Is this method also available as a public builder method, or is it only used for configuration.
     *
     * @return whether the method is also a builder method ({@code true}), or only config method ({@code false})
     */
    boolean builderMethod() default true;

    /**
     * This can be set to {@code false} for options, where this annotation is used for other purposes, and in fact does not
     * indicate something to be loaded from configuration.
     *
     * @return whether this option should be configured from configuration
     */
    boolean configured() default true;

    /**
     * Option kind.
     */
    enum Kind {
        /**
         * Option is a single value (leaf node).
         * Example: server port
         */
        VALUE,
        /**
         * Option is a list of values (either primitive, String or object nodes).
         * Example: cipher suite in SSL, server sockets
         */
        LIST,
        /**
         * Option is a map of strings to primitive type or String.
         * Example: tags in tracing, CDI configuration
         */
        MAP
    }
}
