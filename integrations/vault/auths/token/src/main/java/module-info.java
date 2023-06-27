/*
 * Copyright (c) 2021, 2023 Oracle and/or its affiliates.
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

import io.helidon.common.features.api.Feature;
import io.helidon.common.features.api.HelidonFlavor;

/**
 * Token authentication method for Vault.
 */
@Feature(value = "Token",
        description = "Token Authentication Method",
        in = {HelidonFlavor.MP, HelidonFlavor.SE, HelidonFlavor.NIMA},
        path = {"HCP Vault", "Auth", "Token"}
)
module io.helidon.integrations.vault.auths.token {
    requires static io.helidon.common.features.api;

    requires transitive io.helidon.integrations.vault;
    requires io.helidon.integrations.common.rest;
    requires io.helidon.integrations.vault.auths.common;
    requires io.helidon.common.http;
    requires io.helidon.nima.webclient;

    exports io.helidon.integrations.vault.auths.token;

    provides io.helidon.integrations.vault.spi.AuthMethodProvider
            with io.helidon.integrations.vault.auths.token.TokenAuthProvider;

    provides io.helidon.integrations.vault.spi.VaultAuth
            with io.helidon.integrations.vault.auths.token.TokenVaultAuth;

    provides io.helidon.integrations.vault.spi.InjectionProvider
            with io.helidon.integrations.vault.auths.token.TokenAuthProvider;
}
