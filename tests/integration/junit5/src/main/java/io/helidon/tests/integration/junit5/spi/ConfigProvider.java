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
package io.helidon.tests.integration.junit5.spi;

import io.helidon.config.Config;
import io.helidon.tests.integration.junit5.SuiteExtensionProvider;

/**
 * Helidon integration tests configuration provider.
 */
public interface ConfigProvider extends SuiteExtensionProvider {

    /**
     * Config file name from {@link io.helidon.tests.integration.junit5.TestConfig}
     * annotation.
     *
     * @param file config file name to read from classpath
     */
    void file(String file);

    /**
     * Build configuration builder with default initial values.
     */
    void setup();

    /**
     * Provide config builder to be used in setup hook.
     *
     * @return configuration builder with values from provided file.
     */
    Config.Builder builder();

    /**
     * Start the existence of Config.
     */
    void start();

    /**
     * Provide root {@link Config} instance for the tests.
     */
    Config config();

    /**
     * Cast {@link io.helidon.tests.integration.junit5.SuiteExtensionProvider} as {@link ConfigProvider}.
     * Implementing class should override this method and add itself.
     *
     * @param cls {@link io.helidon.tests.integration.junit5.SuiteExtensionProvider} child interface or implementing class
     * @return this instance cast to {@link ConfigProvider} and optionally implementing class
     * @param <T> target casting type
     */
    @Override
    default <T extends SuiteExtensionProvider> T as(Class<T> cls) {
        if (cls == ConfigProvider.class) {
            return cls.cast(this);
        }
        throw new IllegalArgumentException(
                String.format("Cannot cast this ConfigProvider implementation as %s", cls.getName()));
    }

}
