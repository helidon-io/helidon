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

package io.helidon.webserver.testing.junit5.spi;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;

/**
 * Common interface for JUnit extensions that can extend features of the
 * {@link io.helidon.webserver.testing.junit5.ServerTest} or
 * {@link io.helidon.webserver.testing.junit5.RoutingTest}.
 */
public interface HelidonJunitExtension extends BeforeAllCallback,
                                               AfterAllCallback,
                                               BeforeEachCallback,
                                               AfterEachCallback {

    @Override
    default void afterAll(ExtensionContext context) {
    }

    @Override
    default void afterEach(ExtensionContext context) {
    }

    @Override
    default void beforeAll(ExtensionContext context) {
    }

    @Override
    default void beforeEach(ExtensionContext context) {
    }

    /**
     * Does this extension support the provided parameter.
     *
     * @param parameterContext parameter context
     * @param extensionContext extension context
     * @return {@code true} if the parameter is supported by this extension, {@code false} otherwise
     * @throws ParameterResolutionException in case the parameter cannot be correctly resolved
     */
    default boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
            throws ParameterResolutionException {
        return false;
    }
}
