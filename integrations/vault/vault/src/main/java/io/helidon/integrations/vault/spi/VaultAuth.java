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

import java.util.Optional;

import io.helidon.config.Config;
import io.helidon.integrations.common.rest.RestApi;
import io.helidon.integrations.vault.Vault;

/**
 * Java Service Loader service providing means of authenticating this Vault client
 * with a Vault instance.
 */
public interface VaultAuth {
    /**
     * Provide {@link io.helidon.integrations.common.rest.RestApi} to use with {@link io.helidon.integrations.vault.Vault}
     * operations.
     *
     * @param config configuration (may be empty if not provided by user)
     * @param vaultBuilder vault builder
     * @return {@link io.helidon.integrations.common.rest.RestApi} in case this Vault authentication can authenticate
     */
    Optional<RestApi> authenticate(Config config, Vault.Builder vaultBuilder);
}
