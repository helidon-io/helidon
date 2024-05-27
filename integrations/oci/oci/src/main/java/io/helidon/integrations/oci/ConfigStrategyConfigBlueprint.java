/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.integrations.oci;

import java.util.Optional;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.common.configurable.Resource;

/**
 * Configuration of the {@code config} authentication strategy.
 */
@Prototype.Blueprint
@Prototype.Configured
interface ConfigStrategyConfigBlueprint {
    /**
     * The OCI region.
     *
     * @return the OCI region
     */
    @Option.Configured
    String region();

    /**
     * The OCI authentication fingerprint.
     * <p>
     * This configuration property must be provided in order to set the <a
     * href="https://docs.oracle.com/en-us/iaas/Content/API/Concepts/apisigningkey.htm">API signing key's fingerprint</a>.
     * See {@linkplain com.oracle.bmc.auth.SimpleAuthenticationDetailsProvider#getFingerprint()} for more details.
     *
     * @return the OCI authentication fingerprint
     */
    @Option.Configured
    String fingerprint();

    /**
     * The OCI authentication private key resource.
     * A resource can be defined as a resource on classpath, file on the file system,
     * base64 encoded text value in config, or plain-text value in config.
     * <p>
     * If not defined, we will use {@code .oci/oic_api_key.pem} file in user home directory.
     *
     * @return the OCI authentication key file
     */
    @Option.Configured
    Optional<Resource> privateKey();

    /**
     * The OCI authentication passphrase.
     * <p>
     * This property must be provided in order to set the
     * {@linkplain com.oracle.bmc.auth.SimpleAuthenticationDetailsProvider#getPassphraseCharacters()}.
     *
     * @return the OCI authentication passphrase
     */
    @Option.Configured
    @Option.Confidential
    char[] passphrase();

    /**
     * The OCI tenant id.
     * <p>
     * This property must be provided in order to set the
     * {@linkplain com.oracle.bmc.auth.SimpleAuthenticationDetailsProvider#getTenantId()}.
     *
     * @return the OCI tenant id
     */
    @Option.Configured
    String tenantId();

    /**
     * The OCI user id.
     * <p>
     * This property must be provided in order to set the
     * {@linkplain com.oracle.bmc.auth.SimpleAuthenticationDetailsProvider#getUserId()}.
     *
     * @return the OCI user id
     */
    @Option.Configured
    String userId();
}
