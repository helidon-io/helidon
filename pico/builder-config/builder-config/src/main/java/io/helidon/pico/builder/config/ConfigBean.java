/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.pico.builder.config;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import io.helidon.pico.builder.BuilderTrigger;

/**
 * A ConfigBean is intended to aggregate and encapsulate all of the logical attributes that a service needs from the
 * configuration sub-system.
 *
 * Apply this annotation on a "pure" configuration bean interface, e.g.
 * <pre>{@code
 * @ConfigBean
 * public interface MyConfigBean {
 *     String getName();
 *     int getPort();
 * }
 * }</pre>
 *
 * When the Pico Config apt/annotation processor is applied during compilation, all service interfaces found with this annotation
 * will trigger an implementation of that interface to be code-generated. The generated class will support the fluent builder
 * pattern, and will be fully integrated with Helidon's config subsystem (module: config/config).
 * <p>
 * The companion to this annotation is {@link ConfiguredBy} - this is used on the service interface itself, declaring it to (a) be
 * configured by the {@link ConfigBean}-annotated, bean-like interface, and (b) to optionally "driven
 * by" that configuration; meaning that for each instance of the config bean in the config bean registry (see SPI module), a
 * cooresponding service instance will be paired to this configuration.
 *
 * @see ConfiguredBy
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(java.lang.annotation.ElementType.TYPE)
@BuilderTrigger
public @interface ConfigBean {

    /**
     * Determines whether an instance of this config bean in the registry will cause the backing service to be activated.
     *
     * @return true if the presence of the config bean has an activation affect (aka, "config driven services")
     */
    boolean drivesActivation() default true;

    /**
     * An instance of this bean will be created iff there are no instances discovered by the configuration provider(s) post
     * startup, and will use all default values annotated on the bean interface.
     *
     * @return use the default config instance
     */
    boolean defaultConfigBeanUsingDefaults() default false;

    /**
     * Determines whether there can be more than one bean instance of this type.
     *
     * If false then only 0..1 behavior will be permissible for active beans in the config registry. If true then {@code > 1}
     * instances will be permitted. The default values is {@code true}.
     * <p>
     * Note: this attribute is dynamic in nature, and therefore cannot be validated at compile time. All violations found to this
     * policy will be observed during PicoServices activation.
     *
     * @return true if repeatable
     */
    boolean repeatable() default true;

    /**
     * The overridden key to use. If not set this will default to use the expanded name from the config subsystem,
     * (e.g. MyConfig -> "my-config").
     *
     * @return the overriding key to use
     */
    String key() default "";

}
