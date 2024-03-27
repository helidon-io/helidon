/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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
package io.helidon.tests.integration.junit5;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import io.helidon.tests.integration.junit5.spi.ConfigProvider;

/**
 * Test {@link Suite} that requires configuration.
 * Configuration is read from specified file on the classpath. Configuration may be optionally
 * stored into {@link SuiteContext#storage()}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Inherited
public @interface TestConfig {

    /**
     * Test config provider class.
     * Default value is {@link DefaultConfigProvider}.class
     *
     * @return config provider class
     */
    Class<? extends ConfigProvider> provider() default DefaultConfigProvider.class;


    /**
     * Test config provider configuration file.
     * Default value is {@code "test.yaml"}
     *
     * @return config file to read from classpath
     */
    String file() default "test.yaml";

}
