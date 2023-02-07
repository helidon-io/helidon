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

package io.helidon.pico.configdriven;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.inject.Qualifier;

/**
 * In support of config-driven services. Place this on a service type interface where {@link jakarta.inject.Singleton} would
 * typically go, for example as follows:
 * <pre>
 * {@code
 * @ConfiguredBy(MyConfigBean.class)
 * public class MyConfiguredService {
 *     @Inject
 *     public MyConfiguredService(MyConfigBean cfg) {
 *         ...
 *     }
 *     ...
 * }}
 * </pre>
 *
 * @see io.helidon.builder.config.ConfigBean
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(java.lang.annotation.ElementType.TYPE)
@Qualifier
public @interface ConfiguredBy {

    /**
     * The {@link io.helidon.builder.config.ConfigBean}-annotated type.
     *
     * @return the {@code ConfigBean}-annotated type.
     */
    Class<?> value();

    /**
     * Required to be set to true if {@link #drivesActivation()} is
     * applied, thereby overriding the defaults in {@link io.helidon.builder.config.ConfigBean} annotation.
     *
     * @return true to override the config bean attributes, false to defer to the bean
     */
    boolean overrideBean() default false;

    /**
     * Determines whether an instance of this config bean in the registry will cause the backing service to be activated.
     * Note that {@link #overrideBean()} must be set to true for this to be applied.
     *
     * @return true if the presence of the config bean has an activation affect (aka, "config-driven services")
     */
    boolean drivesActivation() default true;

}
