/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.service.inject.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A set of annotations and other types for using config beans and services that are
 * created for configuration in general.
 * If you want to use {@link ConfigDriven.ConfigBean} annotation, at least
 * Helidon common config must be on the classpath. To make use of it, you should also include an implementation
 * ({@code helidon-config}, or maybe {@code helidon-config-yaml}).
 * <p>
 * <b>Note</b> that config is not on the classpath by default, as it is not required for Service registry itself.
 * <p>
 * A configuration bean works as follows:
 * <ol>
 *     <li>Config instance is discovered (expected to be a service in the service registry, provided for example by Helidon
 *     Config)</li>
 *     <li>The generated config bean acts as a {@link io.helidon.service.inject.api.Injection.ServicesProvider}
 *          for the service type</li>
 *     <li>Based on the annotations in this type, instance(s) would be created</li>
 *     <li>These instances can be lookup up from the registry, or can be used to drive instances of other services using
 *     {@link io.helidon.service.inject.api.Injection.CreateFor}</li>
 * </ol>
 */
public final class ConfigDriven {
    private ConfigDriven() {
    }

    /**
     * This configured type should be acting as a config bean. This means that if appropriate configuration
     * exists (must be a root configured type with a defined prefix), an instance will be created from that configuration.
     * Additional setup is possible to ensure an instance even if not present in config, and to create default instance.
     * <p>
     * Placing this annotation on any type makes that type configurable from the root of configuration, even if the prototype
     * configuration says otherwise (there is no validation for this), {@link #value()} is used as the configuration key.
     * <p>
     * Any type can be annotated with this annotation as long as it has accessible (non-private) methods::
     * <ul>
     *     <li>{@code static Type create(io.helidon.common.config.Config)} method</li>
     *     <li>{@code static Type create()} method if also annotated with
     *     {@link io.helidon.service.inject.api.ConfigDriven.AddDefault}</li>
     * </ul>
     */
    @Documented
    @Retention(RetentionPolicy.CLASS)
    @Target({java.lang.annotation.ElementType.TYPE,
            ElementType.FIELD,
            ElementType.PARAMETER})
    @Injection.Qualifier
    public @interface ConfigBean {
        /**
         * Configuration key to be used to discover location in config.
         * We expect this annotation to be placed on a Blueprint of a configured prototype (see Helidon Builder), in which
         * case we use the key defined on the configured annotation.
         * If a non-empty string value is defined on this annotation, it will ALWAYS override any other key configuration
         * <p>
         * If the annotated type is not a Blueprint, the key must be defined on this annotation.
         * In such a case the type must provide an accessible factory method (at least package local)
         * "{@code static MyConfigBean create(io.helidon.common.config.Config)}", if the type is also annotated
         * with either of {@link ConfigDriven.AtLeastOne} or
         * {@link io.helidon.service.inject.api.ConfigDriven.AddDefault}, an additional factory method must exist:
         * "{@code static MyConfigBean create()}".
         * <p>
         * If places we look for yield an empty string key, we actually use the root configuration.
         *
         * @return configuration key to discover configuration of this bean
         */
        String value() default "";
    }

    /**
     * At least one instance is required in configuration.
     */
    @Documented
    @Retention(RetentionPolicy.CLASS)
    @Target(java.lang.annotation.ElementType.TYPE)
    @Injection.Qualifier
    public @interface AtLeastOne {
    }

    /**
     * Determines whether there can be more than one bean instance of this type configured.
     * <p>
     * If not defined on a config bean, there can be only up to one configured instance.
     * If defined, one instance will be created for each list value, or child node of the config tree.
     * <p>
     * Note: this is dynamic in nature, and therefore cannot be validated at compile time. All violations found to this
     * policy will be observed during <i>Services</i> activation.
     */
    @Documented
    @Retention(RetentionPolicy.CLASS)
    @Target(java.lang.annotation.ElementType.TYPE)
    @Injection.Qualifier
    public @interface Repeatable {
    }

    /**
     * There will always be an instance created with defaults.
     */
    @Documented
    @Retention(RetentionPolicy.CLASS)
    @Target(java.lang.annotation.ElementType.TYPE)
    @Injection.Qualifier
    public @interface AddDefault {
    }

    /**
     * There will always be exactly one instance, either from config, or default.
     * This annotation can only be combined with {@link io.helidon.service.inject.api.ConfigDriven.ConfigBean}.
     * <p>
     * If applied, the generated config bean types will be singletons, without a name (as there is always exactly one instance).
     */
    @Documented
    @Retention(RetentionPolicy.CLASS)
    @Target(java.lang.annotation.ElementType.TYPE)
    @Injection.Qualifier
    public @interface OrDefault {
    }
}
