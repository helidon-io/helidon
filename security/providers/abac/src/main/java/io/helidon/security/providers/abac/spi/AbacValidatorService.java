/*
 * Copyright (c) 2018, 2021 Oracle and/or its affiliates.
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

package io.helidon.security.providers.abac.spi;

import io.helidon.config.Config;
import io.helidon.security.providers.abac.AbacValidatorConfig;

/**
 * Service to use with ServiceLoader to map configuration to
 * {@link AbacValidator}.
 */
public interface AbacValidatorService {
    /**
     * Key of the "root" of configuration of this validator.
     * <p>
     * Example - scope validator, the configuration in yaml may then be:
     * <pre>
     * security.providers:
     *   - abac:
     *     fail-on-unvalidated: true
     *     scope:
     *      ....
     * </pre>
     *
     * @return name of the configuration key
     */
    String configKey();

    /**
     * Create a new instance of the validator based on the configuration
     * provided. The config is located at the config key of this provider.
     *
     * @param config Config with provider configuration
     * @return validator instance created from the {@link Config} provided
     */
    AbacValidator<? extends AbacValidatorConfig> instantiate(Config config);
}
