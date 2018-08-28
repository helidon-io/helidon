/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.security.spi;

import io.helidon.config.Config;

/**
 * Service to use with ServiceLoader to map configuration to
 * provider.
 */
public interface SecurityProviderService {
    /**
     * Key of the "root" of configuration of this provider.
     * <p>
     * Example - Http Signature Provider may use "http-signatures", the configuration in yaml may then be:
     * <pre>
     * security.providers:
     *   - http-signatures:
     *     inbound:
     *      ....
     * </pre>
     *
     * The name of the provider is the same string, unless explicitly defined
     *
     * @return name of the configuration key
     */
    String providerConfigKey();

    /**
     * Class of the provider of this provider service.
     * The class may be used for cases where configuration requires
     * explicit class name (e.g. when multiple providers use the
     * same configuration key).
     *
     * @return class of {@link SecurityProvider} provided by this provider service
     */
    Class<? extends SecurityProvider> getProviderClass();

    /**
     * Create a new instance of the provider based on the configuration
     * provided. The config is located at the config key of this provider.
     *
     * @param config Config with provider configuration
     * @return provider instance created from the {@link Config} provided
     */
    SecurityProvider getProviderInstance(Config config);
}
