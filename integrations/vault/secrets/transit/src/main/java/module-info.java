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
 * Vault transit secrets.
 *
 * @see io.helidon.integrations.vault.secrets.transit.TransitSecrets
 */
@Feature(value = "Transit",
        description = "Transit Secrets Engine",
        in = {HelidonFlavor.SE, HelidonFlavor.MP, HelidonFlavor.NIMA},
        path = {"HCP Vault", "Secrets", "Transit"}
)
module io.helidon.integrations.vault.secrets.transit {
    requires static io.helidon.common.features.api;

    requires jakarta.json;

    requires io.helidon.integrations.common.rest;
    requires transitive io.helidon.integrations.vault;
    requires io.helidon.common.http;
    requires transitive io.helidon.security;

    exports io.helidon.integrations.vault.secrets.transit;

    provides io.helidon.integrations.vault.spi.SecretsEngineProvider
            with io.helidon.integrations.vault.secrets.transit.TransitEngineProvider;

    provides io.helidon.security.spi.SecurityProviderService
            with io.helidon.integrations.vault.secrets.transit.TransitSecurityService;

    provides io.helidon.integrations.vault.spi.InjectionProvider
            with io.helidon.integrations.vault.secrets.transit.TransitEngineProvider;
}