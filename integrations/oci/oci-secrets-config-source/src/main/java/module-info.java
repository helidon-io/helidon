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

/**
 * Provides packages containing classes and interfaces for Oracle Cloud Infrastructure (OCI) <a
 * href="https://docs.oracle.com/en-us/iaas/tools/java/latest/com/oracle/bmc/secrets/package-summary.html">Secrets
 * Retrieval</a> and <a
 * href="https://docs.oracle.com/en-us/iaas/tools/java/latest/com/oracle/bmc/vault/package-summary.html">Vault</a>
 * API-using {@linkplain io.helidon.config.spi.ConfigSource configuration sources}.
 *
 * @see io.helidon.integrations.oci.secrets.configsource.OciSecretsConfigSourceProvider
 */
@SuppressWarnings({ "requires-automatic", "requires-transitive-automatic" })
module io.helidon.integrations.oci.secrets.configsource {

    exports io.helidon.integrations.oci.secrets.configsource;

    requires io.helidon.common;
    requires transitive io.helidon.config;
    requires io.helidon.integrations.oci.sdk.runtime;
    requires oci.java.sdk.common;
    requires oci.java.sdk.secrets;
    requires oci.java.sdk.vault;

    provides io.helidon.config.spi.ConfigSourceProvider with io.helidon.integrations.oci.secrets.configsource.OciSecretsConfigSourceProvider;

}
