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

package io.helidon.integrations.oci.tls.certificates;

import java.net.URI;
import java.util.Optional;
import java.util.function.Supplier;

import io.helidon.config.metadata.ConfiguredOption;

/**
 * Blueprint configuration for {@link OciCertificatesTlsManager}.
 */
//@Prototype.Blueprint
//@Configured
interface OciCertificatesTlsManagerConfigBlueprint /*extends Prototype.Factory<OciCertificatesTlsManager>*/ {

    /**
     * The schedule for trigger a reload check, testing whether there is a new certificate available.
     *
     * @return the schedule for reload
     */
    @ConfiguredOption
    String schedule();

    /**
     * The address to use for the OCI Key Management Service / Vault crypto usage.
     * Each OCI Vault has public crypto and management endpoints. We need to specify the crypto endpoint of the vault we are
     * rotating the private keys in. The implementation expects both client and server to store the private key in the same vault.
     *
     * @return the address for the key management service / vault crypto usage
     */
    @ConfiguredOption
    URI vaultCryptoEndpoint();

    /**
     * The address to use for the OCI Key Management Service / Vault management usage.
     * The crypto endpoint of the vault we are rotating the private keys in.
     *
     * @return the address for the key management service / vault management usage
     */
    @ConfiguredOption
    Optional<URI> vaultManagementEndpoint();

    /**
     * The OCID of the compartment the services are in.
     *
     * @return the compartment OCID
     */
    @ConfiguredOption
    Optional<String> compartmentOcid();

    /**
     * The Certificate Authority OCID.
     *
     * @return certificate authority OCID
     */
    @ConfiguredOption
    String caOcid();

    /**
     * The Certificate OCID.
     *
     * @return certificate OCID
     */
    @ConfiguredOption
    String certOcid();

    /**
     * The Key OCID.
     *
     * @return key OCID
     */
    @ConfiguredOption
    String keyOcid();

    /**
     * The Key password.
     *
     * @return key password
     */
    @ConfiguredOption
    Supplier<char[]> keyPassword();

}
