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

package io.helidon.builder.config;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import io.helidon.builder.BuilderTrigger;

/**
 * A {@code ConfigBean} is another {@link io.helidon.builder.BuilderTrigger} that extends the
 * {@link io.helidon.builder.Builder} concept to support the integration to Helidon's configuration sub-system. It basically
 * provides everything that {@link io.helidon.builder.Builder} provides. However, unlike the base
 * {@link io.helidon.builder.Builder} generated classes that can handle any object type, the types used within your target
 * {@code ConfigBean}-annotated interface must have all of its attribute getter method types resolvable by Helidon's configuration
 * sub-system.
 * <p>
 * The @code ConfigBean} is therefore a logical grouping for the "pure configuration" set of attributes (and
 * sub-<i>ConfigBean</i> attributes) that typically originate from an external media store (e.g., property files, config maps,
 * etc.), and are integrated via Helidon's {@link io.helidon.common.config.Config} subsystem at runtime.
 * <p>
 * One should write a {@code ConfigBean}-annotated interface in such a way as to group the collection of configurable elements
 * that logically belong together to then be delivered (and perhaps drive an activation of) one or more java service types that
 * are said to be {@code ConfiguredBy} the given {@link ConfigBean} instance.
 * <p>
 * The {@code builder-config-processor} module is required to be on the APT classpath to code-generate the implementation
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
 * When {@code Pico} services are incorporated into the application lifecycle at runtime, the configuration
 * sub-system is scanned at startup and {@code ConfigBean} instances are created and fed into the {@code ConfigBeanRegistry}.
 * This mapping occurs based upon the {@link io.helidon.config.metadata.ConfiguredOption#key()} on each of
 * the {@code ConfigBean}'s attributes. If no such {@code ConfiguredOption} is found then the type name is used as the key
 * (e.g., MyConfigBean would map to "my-config-bean").
 * <p>
 * Also see {@code ConfiguredBy} in Pico's config-driven module.
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
    String value() default "";

    /**
     * Determines whether an instance of this config bean in the bean registry will result in the backing service
     * {@code ConfiguredBy} this bean to be activated.
     * <p>
     * As of the current release, only {@link LevelType#ROOT} level config beans can drive activation.
     * <p>
     * The default value is {@code false}.
     *
     * @return true if this config bean should drive {@code ConfiguredBy} service activation
     */
    boolean drivesActivation() default false;

    /**
     * An instance of this bean will be created if there are no instances discovered by the configuration provider(s) post
     * startup, and will use all default values annotated using {@code ConfiguredOptions} from the bean interface methods.
     * <p>
     * The default value is {@code false}.
     *
     * @return the default config bean instance using defaults
     */
    boolean atLeastOne() default false;

    /**
     * Determines whether there can be more than one bean instance of this type.
     * <p>
     * If false then only 0..1 behavior will be permissible for active beans in the config registry. If true then {@code > 1}
     * instances will be permitted.
     * <p>
     * Note: this attribute is dynamic in nature, and therefore cannot be validated at compile time. All violations found to this
     * policy will be observed during PicoServices activation.
     * <p>
     * The default value is {@code true}.
     *
     * @return true if repeatable
     */
    boolean repeatable() default false;

    /**
     * An instance of this bean will be created if there are no instances discovered by the configuration provider(s) post
     * startup, and will use all default values annotated on the bean interface.
     * <p>
     * As of the current release, only {@link LevelType#ROOT} level config beans can be defaulted.
     * <p>
     * The default value is {@code false}.
     *
     * @return use the default config instance
     */
    boolean wantDefaultConfigBean() default false;

    /**
     * The {@link LevelType} of this config bean.
     * <p>
     * The default level type is {@link LevelType#ROOT}.
     *
     * @return the level type of this config bean
     */
    LevelType levelType() default LevelType.ROOT;


    /**
     * Represents the level in the config tree to search for config bean instances. Currently, only
     * {@link ConfigBean.LevelType#ROOT} is supported.
     */
    enum LevelType {
        /**
         * The config bean {@link #value()} must be at the root of the {@link io.helidon.common.config.ConfigValue} tree in order
         * to trigger config bean instance creation.
         * <p>
         * As of the current release, only {@code ROOT} level config beans can {@link #drivesActivation()}.
         */
        ROOT,

        /**
         * The config bean {@link #value()} must be at a depth > 0 of the config tree.
         * As of the current release, {@code NESTED} config beans are unable to {@link #drivesActivation()}.
         */
        NESTED,

    }

}
