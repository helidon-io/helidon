/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

package io.helidon.microprofile.tests.junit5;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Additional configuration of config itself.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Inherited
public @interface Configuration {
    /**
     * If set to {@code true}, the existing (or default) MicroProfile configuration would be used.
     * By default uses a configuration constructed using all {@link io.helidon.microprofile.tests.junit5.AddConfig}
     * annotations and {@link #configSources()}.
     * When set to false and a {@link org.junit.jupiter.api.BeforeAll} method registers a custom configuration
     * with {@link org.eclipse.microprofile.config.spi.ConfigProviderResolver}, the result is undefined, though
     * tests have shown that the registered config may be used (as BeforeAll ordering is undefined by
     * JUnit, it may be called after our extension)
     *
     * @return whether to use existing (or default) configuration, or customized one
     */
    boolean useExisting() default false;

    /**
     * Class path properties config sources to add to configuration of this test class or method.
     *
     * @return config sources to add
     */
    String[] configSources() default {};

    /**
     * Configuration Profile.
     * By default it is set to "test".
     *
     * @return test profile
     */
    String profile() default "test";
}
