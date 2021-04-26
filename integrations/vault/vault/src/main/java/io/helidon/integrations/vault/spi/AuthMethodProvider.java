/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

package io.helidon.integrations.vault.spi;

import io.helidon.config.Config;
import io.helidon.integrations.common.rest.RestApi;
import io.helidon.integrations.vault.AuthMethod;

/**
 * A Java Service Loader SPI to support additional auth methods of Vault.
 *
 * @param <T> type of the API supported by this provider
 */
public interface AuthMethodProvider<T> {
    /**
     * Supported method by this provider.
     *
     * @return method that is supported, used to choose the correct provider for a method
     * @see io.helidon.integrations.vault.Vault#auth(io.helidon.integrations.vault.AuthMethod)
     */
    AuthMethod<T> supportedMethod();

    /**
     * Create an auth instance to provide API to access this method.
     *
     * @param config configuration that can be used to customize the engine
     * @param restAccess to access REST API of the vault, preconfigured with token
     * @param path path of this auth method instance
     *
     * @return a new secrets instance to be used to access secrets
     */
    T createAuth(Config config, RestApi restAccess, String path);
}
