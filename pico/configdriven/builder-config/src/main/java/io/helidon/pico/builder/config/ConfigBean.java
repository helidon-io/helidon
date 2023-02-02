/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

import io.helidon.builder.BuilderTrigger;

/**
 * A {@code ConfigBean} is another {@link io.helidon.builder.BuilderTrigger} which extends the
 * {@link io.helidon.builder.Builder} concept in support of integration to Helidon's configuration sub-system. It provides
 * everything that {@link io.helidon.builder.Builder} provides. However, unlike the base
 * {@link io.helidon.builder.Builder} generated classes which can handle any object type, the types used within your target
 * {@code ConfigBean}-annotated interface must have all of its attribute getter method types resolvable by Helidon's configuration
 * sub-system.
 * <p>
 * One should write a {@code ConfigBean}-annotated interface in such a way as to group the collection of configurable elements
 * that logically belong together to then be delivered (and perhaps trigger an activation of) one or more java service types that
 * are said to be {@code ConfiguredBy} the given {@link ConfigBean} instance.
 * <p>
 * The {@code pico-builder-config-processor} module is required to be on the APT classpath to code-generate the implementation
 * classes for the {@code ConfigBean}.
 * <p>
 * Example:
 * <pre>{@code
 * @ConfigBean
 * public interface MyConfigBean {
 *     String getName();
 *     int getPort();
 * }
 * }</pre>
 * <p>
 * When {@code Pico} services and config-service modules are incorporated into the application lifecycle, the configuration
 * sub-system is scanned at startup and {@code ConfigBean} instances are created and fed into the {@code ConfigBeanRegistry}.
 * This mapping occurs based upon the {@link io.helidon.config.metadata.ConfiguredOption#key()} applied on the {@code ConfigBean}
 * interface type. If no such declaration is found, then the type name is used as the key (e.g., MyConfigBean would map to
 * "my-config-bean").
 *
 * @see ConfiguredBy
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(java.lang.annotation.ElementType.TYPE)
@BuilderTrigger
public @interface ConfigBean {

    /**
     * The overridden key to use. If not set this will default to use the expanded name from the config subsystem,
     * (e.g. MyConfig -> "my-config").
     *
     * @return the overriding key to use
     */
    String key() default "";

    /**
     * Determines whether an instance of this config bean in the bean registry will result in the backing service
     * {@code ConfiguredBy} this bean to be activated.
     *
     * @return true if this config bean should drive {@code ConfiguredBy} service activation
     */
    boolean drivesActivation() default true;

    /**
     * An instance of this bean will be created if there are no instances discovered by the configuration provider(s) post
     * startup, and will use all default values annotated using {@code ConfiguredOptions} from the bean interface methods.
     *
     * @return the default config bean instance using defaults
     */
    boolean atLeastOne() default false;

    /**
     * Determines whether there can be more than one bean instance of this type.
     * <p>
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
     * An instance of this bean will be created iff there are no instances discovered by the configuration provider(s) post
     * startup, and will use all default values annotated on the bean interface.
     *
     * @return use the default config instance
     */
    boolean wantDefaultConfigBean() default false;

}
