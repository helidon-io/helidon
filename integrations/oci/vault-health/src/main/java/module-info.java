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

/**
 * OCI Vault Health.
 */
module io.helidon.integrations.oci.vault.health {
    requires io.helidon.security;
    requires io.helidon.common.reactive;
    requires io.helidon.integrations.oci.connect;
    requires java.json;

    requires io.helidon.integrations.common.rest;
    requires io.helidon.common.http;
    requires io.helidon.health;
    requires io.helidon.health.common;
    requires io.helidon.integrations.oci.vault;

    requires static microprofile.config.api;
    requires static jakarta.enterprise.cdi.api;
    requires static jakarta.inject.api;
    requires java.logging;

    exports io.helidon.integrations.oci.vault.health;

    opens io.helidon.integrations.oci.vault.health to weld.core.impl, io.helidon.microprofile.cdi;
}