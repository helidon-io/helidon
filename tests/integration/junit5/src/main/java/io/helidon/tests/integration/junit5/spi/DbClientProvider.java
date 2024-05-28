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

import io.helidon.dbclient.DbClient;
import io.helidon.tests.integration.junit5.SuiteExtensionProvider;

/**
 * Helidon Database Client integration tests configuration provider interface.
 */
public interface DbClientProvider extends SuiteExtensionProvider {

    /**
     * Build configuration builder with default initial values.
     */
    void setup();

    /**
     * Provide config builder to be used in setup hook.
     *
     * @return configuration builder with values from provided file.
     */
    DbClient.Builder builder();

    /**
     * Start the existence of {@link DbClient}.
     */
    void start();

    /**
     * Provide root {@link DbClient} instance for the tests.
     */
    DbClient dbClient();

    /**
     * Cast {@link io.helidon.tests.integration.junit5.SuiteExtensionProvider} as {@link DbClientProvider}.
     * Implementing class should override this method and add itself.
     *
     * @param cls {@link io.helidon.tests.integration.junit5.SuiteExtensionProvider} child interface or implementing class
     * @return this instance cast to {@link DbClientProvider} and optionally implementing class
     * @param <T> target casting type
     */
    @Override
    default <T extends SuiteExtensionProvider> T as(Class<T> cls) {
        if (cls == DbClientProvider.class) {
            return cls.cast(this);
        }
        throw new IllegalArgumentException(
                String.format("Cannot cast this DbClientProvider implementation as %s", cls.getName()));
    }

}
