/*
 * Copyright (c) 2020, 2025 Oracle and/or its affiliates.
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

package io.helidon.microprofile.testing;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * General setting for the test configuration.
 * <p>
 * If used on a method, the container will be reset regardless of the test lifecycle.
 *
 * @see AddConfig
 * @see io.helidon.microprofile.testing.AddConfigs
 * @see io.helidon.microprofile.testing.AddConfigBlock
 */
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface Configuration {
    /**
     * If set to {@code false}, the synthetic test configuration is used.
     * <p>
     * The synthetic test configuration is expressed with the following:
     * <ul>
     *     <li>{@link #configSources()}</li>
     *     <li>{@link #profile()}</li>
     *     <li>{@link AddConfig}</li>
     *     <li>{@link AddConfigs}</li>
     *     <li>{@link AddConfigBlock}</li>
     * </ul>
     * <p>
     * If set to {@code true}, only the existing (or default) MicroProfile configuration is used
     * and the annotations listed previously are ignored.
     * <p>
     * You can use {@link org.eclipse.microprofile.config.spi.ConfigProviderResolver ConfigProviderResolver} to define
     * the configuration programmatically before the CDI container starts.
     *
     * @return whether to use existing (or default) configuration
     */
    boolean useExisting() default false;

    /**
     * Class-path resources to add as config sources to the synthetic test configuration.
     *
     * @return config sources
     */
    String[] configSources() default {};

    /**
     * Configuration profile.
     * <p>
     * The default profile is 'test'
     *
     * @return profile
     */
    String profile() default "test";
}
