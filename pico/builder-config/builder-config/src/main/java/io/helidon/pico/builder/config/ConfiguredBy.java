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

import jakarta.inject.Qualifier;

/**
 * This annotation is placed on a service to that service to be configured by the {@link ConfigBean}-annotated, bean-like
 * interface.
 *
 * For example:
 * <pre>{@code
 * @ConfiguredBy(MyConfigBean.class)
 * public class MyConfiguredService {
 *     @Inject
 *     public MyConfiguredService(MyConfigBean cfg) {
 *         ...
 *     }
 *     ...
 * }
 * }</pre>
 *
 * When the Pico Config apt/annotation processor is applied during compilation, all service interfaces found with this annotation
 * will trigger a ServiceProvider/Activator implementation to be code-generated that will (a) be backed by Helidon's configuration
 * sub-system, (b) integrated into Pico's service registry, and (c) optionally will auto-activate depending upon whether
 * {@link #drivesActivation()} is enabled.
 * <p>
 * The companion to this annotation is {@link ConfigBean} - this annotation encapsulates the
 * configuration a {@link ConfiguredBy} service needs, and will also trigger code-generation to occur.
 *
 * @see ConfigBean
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(java.lang.annotation.ElementType.TYPE)
@Qualifier
public @interface ConfiguredBy {

    /**
     * The config bean interface type. This must be a {@link ConfigBean}-annotated type.
     *
     * @return the config bean interface type
     */
    Class<?> value();

    /**
     * Required to be set to true if {@link #drivesActivation()} or {@link #defaultConfigBeanUsingDefaults()} is
     * applied, thereby overriding the defaults in {@link ConfigBean} annotation.
     *
     * @return true to override the config bean attributes, false to defer to the bean
     */
    boolean overrideBean() default false;

    /**
     * Determines whether an instance of this config bean in the registry will cause the backing service to be activated at
     * PicoServices initialization.
     *
     * @return true if the presence of the config bean has an activation affect (aka, "config driven services")
     * @see #overrideBean()
     */
    boolean drivesActivation() default true;

    /**
     * Determines whether an instance of this bean should be created iff there are no configured instances discovered by the
     * backing configuration sub-system during PicoServices initialization. When set to true a bean instance will
     * be created if there is no backing configuration, with default attribute values found on the bean interface.
     *
     * Note that {@link #overrideBean()} must be set to true for this to be applied.
     *
     * @return use the default config instance
     * @see #overrideBean()
     */
    boolean defaultConfigBeanUsingDefaults() default false;

}
