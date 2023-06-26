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

/**
 * Vault integration with CDI.
 * Exposes all APIs discovered through
 * {@link io.helidon.integrations.vault.spi.InjectionProvider} service loader.
 */
module io.helidon.integrations.vault.cdi {
    requires java.logging;

    requires jakarta.inject;
    requires jakarta.cdi;

    requires microprofile.config.api;

    requires transitive io.helidon.integrations.vault;
    requires io.helidon.microprofile.cdi;

    exports io.helidon.integrations.vault.cdi;

    uses io.helidon.integrations.vault.spi.InjectionProvider;

    provides jakarta.enterprise.inject.spi.Extension
            with io.helidon.integrations.vault.cdi.VaultCdiExtension;
}