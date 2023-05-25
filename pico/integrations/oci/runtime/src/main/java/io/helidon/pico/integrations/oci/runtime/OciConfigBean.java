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

package io.helidon.pico.integrations.oci.runtime;

import java.util.List;
import java.util.Optional;

import io.helidon.builder.config.ConfigBean;
import io.helidon.config.metadata.ConfiguredOption;

/**
 * Configuration used by {@link OciAuthenticationDetailsProvider}.
 */
@ConfigBean(OciConfigBean.NAME)
public interface OciConfigBean {

    /**
     * The config is expected to be under this key.
     */
    String NAME = "oci";

    /** primary base url of metadata service. */
    String PRIMARY_METADATA_SERVICE_BASE_URL = "http://169.254.169.254/opc/v2/";

    /** fallback base url of metadata service. */
    String FALLBACK_METADATA_SERVICE_URL = "http://169.254.169.254/opc/v1/";

    /**
     * The list of auth strategies that will be attempted.
     *
     * @return the list of auth strategies
     * @see io.helidon.pico.integrations.oci.runtime.OciAuthenticationDetailsProvider.AuthStrategy
     */
    List<String> authStrategies();

    /**
     * The OCI configuration profile path.
     *
     * @return the OCI configuration profile path
     */
    @ConfiguredOption(key = "config.path")
    Optional<String> configPath();

    /**
     * The OCI configuration profile name.
     *
     * @return the OCI configuration profile name
     */
    @ConfiguredOption(key = "config.profile")
    Optional<String> configProfile();

    /**
     * The OCI authentication profile name.
     *
     * @return the OCI authentication profile name
     */
    @ConfiguredOption(key = "auth.profile")
    Optional<String> authProfile();

    /**
     * The OCI authentication fingerprint.
     *
     * @return the OCI authentication fingerprint
     */
    @ConfiguredOption(key = "auth.fingerprint")
    Optional<String> authFingerprint();

    /**
     * The OCI authentication key file.
     *
     * @return the OCI authentication key file
     */
    @ConfiguredOption(value = "oci_api_key.pem", key = "auth.keyFile")
    String authKeyFile();

    /**
     * The OCI authentication passphrase.
     *
     * @return the OCI authentication passphrase
     */
    // See https://github.com/helidon-io/helidon/issues/6908
    @ConfiguredOption(key = "auth.passphrase"/* securitySensitive = true*/)
    Optional<String> authPassphrase();

    /**
     * The OCI authentication private key.
     *
     * @return the OCI authentication private key
     */
    @ConfiguredOption(key = "auth.private-key"/* securitySensitive = true*/)
    Optional<String> authPrivateKey();

    /**
     * The OCI region.
     *
     * @return the OCI region
     */
    @ConfiguredOption(key = "auth.region")
    Optional<String> authRegion();

    /**
     * The OCI tenant id.
     *
     * @return the OCI tenant id
     */
    @ConfiguredOption(key = "auth.tenant-id")
    Optional<String> authTenantId();

    /**
     * The OCI user id.
     *
     * @return the OCI user id
     */
    @ConfiguredOption(key = "auth.user-id")
    Optional<String> authUserId();

    /**
     * The OCI idms primary hostname.
     *
     * @return the OCI idms primary hostname
     */
    @ConfiguredOption(value = PRIMARY_METADATA_SERVICE_BASE_URL, key = "idms.hostname")
    String idmsPrimaryHostName();

    /**
     * The OCI idms fallback hostname.
     *
     * @return the OCI idms fallback hostname
     * @see OciAvailability
     */
    @ConfiguredOption(value = FALLBACK_METADATA_SERVICE_URL, key = "idms.fallback-hostname")
    Optional<String> idmsFallbackHostName();

    /**
     * The OCI idms connection timeout in millis. This is used to auto-detect availability.
     *
     * @return the OCI connection timeout in millis.
     * @see OciAvailability
     */
    @ConfiguredOption(value = "100", key = "idms.timeout.milliseconds")
    int idmsTimeoutMilliseconds();

    /**
     * Checks whether there is sufficient configuration defined in this bean to be used for simple authentication.
     *
     * @return true if there is sufficient attributes defined for simple OCI authentication
     * @see OciAuthenticationDetailsProvider
     */
    default boolean simpleConfigIsPresent() {
        return authFingerprint().isPresent()
                && authRegion().isPresent()
                && authTenantId().isPresent()
                && authUserId().isPresent();
    }

}
