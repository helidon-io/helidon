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
import io.helidon.config.metadata.ConfiguredValue;

import com.oracle.bmc.auth.ConfigFileAuthenticationDetailsProvider;

/**
 * Configuration used by {@link OciAuthenticationDetailsProvider}.
 *
 * @see OciExtension
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
     * The list of auth strategies that will be attempted by
     * {@link com.oracle.bmc.auth.AbstractAuthenticationDetailsProvider} when one is
     * called for.
     * <ul>
     *      <li>{@code auto} - if present in the list, or if no value
     *          for this property exists, the behavior will be as if {@code
     *          config,config-file,instance-principals,resource-principal}
     *          were supplied instead.</li>
     *      <li>{@code config} - the
     *          {@link com.oracle.bmc.auth.SimpleAuthenticationDetailsProvider}
     *          will be used, customized with other configuration
     *          properties described here.</li>
     *      <li>{@code config-file} - the
     *          {@link com.oracle.bmc.auth.ConfigFileAuthenticationDetailsProvider}
     *          will be used, customized with other configuration
     *          properties described here.</li>
     *      <li>{@code instance-principals} - the
     *          {@link com.oracle.bmc.auth.InstancePrincipalsAuthenticationDetailsProvider}
     *          will be used.</li>
     *      <li>{@code resource-principal} - the
     *          {@link com.oracle.bmc.auth.ResourcePrincipalAuthenticationDetailsProvider}
     *          will be used.</li>
     * </ul>
     * <p>
     * If there are many strategy descriptors supplied, the
     * first one that is deemed to be available or suitable will
     * be used and all others will be ignored.
     *
     * @return the list of auth strategies that will be applied, defaulting to {@code auto}
     * @see io.helidon.pico.integrations.oci.runtime.OciAuthenticationDetailsProvider.AuthStrategy
     */
    @ConfiguredOption(allowedValues = {
            @ConfiguredValue(value = "auto", description = "auto select first applicable"),
            @ConfiguredValue(value = "config", description = "simple authentication provider"),
            @ConfiguredValue(value = "config-file", description = "config file authentication provider"),
            @ConfiguredValue(value = "instance-principals", description = "instance principals authentication provider"),
            @ConfiguredValue(value = "resource-principals", description = "resource principals authentication provider"),
    })
    List<String> authStrategies();

    /**
     * The OCI configuration profile path.
     * <p>
     * This configuration property has an effect only when {@code config-file} is, explicitly or implicitly,
     * present in the value for the {@link #authStrategies()}.
     * When it is present, this property must also be present and then the
     * {@linkplain com.oracle.bmc.ConfigFileReader#parse(String)}
     * method will be passed this value. It is expected to be passed with a
     * valid OCI configuration file path.
     *
     * @return the OCI configuration profile path
     */
    @ConfiguredOption(key = "config.path")
    Optional<String> configPath();

    /**
     * The OCI configuration/auth profile name.
     * <p>
     * This configuration property has an effect only when {@code config-file} is, explicitly or implicitly,
     * present in the value for the {@link #authStrategies()}.
     * When it is present, this property may also be optionally provided in order to override the default {@link
     * com.oracle.bmc.ConfigFileReader#DEFAULT_PROFILE_NAME}.
     *
     * @return the optional OCI configuration/auth profile name
     */
    @ConfiguredOption(key= "config.profile")
    Optional<String> configProfile();

    /**
     * The OCI authentication fingerprint.
     * <p>
     * This configuration property has an effect only when {@code config} is, explicitly or implicitly,
     * present in the value for the {@link #authStrategies()}.
     * When it is present, this property must be provided in order to set the {@link
     * <a href="https://docs.oracle.com/en-us/iaas/Content/API/Concepts/apisigningkey.htm#four"
     * target="_top">API signing key's fingerprint</a>. See
     * {@linkplain ConfigFileAuthenticationDetailsProvider#getFingerprint()}
     * for more details.
     *
     * @return the OCI authentication fingerprint
     */
    @ConfiguredOption(key = "auth.fingerprint")
    Optional<String> authFingerprint();

    /**
     * The OCI authentication key file.
     * <p>
     * This configuration property has an effect only when {@code config} is, explicitly or implicitly,
     * present in the value for the {@link #authStrategies()}.
     * When it is present, this property must be provided in order to set the
     * {@linkplain ConfigFileAuthenticationDetailsProvider#getPemFilePath()}.
     *
     * @return the OCI authentication key file
     */
    @ConfiguredOption(value = "oci_api_key.pem", key = "auth.keyFile")
    String authKeyFile();

    /**
     * The OCI authentication passphrase.
     * <p>
     * This configuration property has an effect only when {@code config} is, explicitly or implicitly,
     * present in the value for the {@link #authStrategies()}.
     * When it is present, this property must be provided in order to set the
     * {@linkplain ConfigFileAuthenticationDetailsProvider#getPassphraseCharacters()}.
     *
     * @return the OCI authentication passphrase
     */
    // See https://github.com/helidon-io/helidon/issues/6908
    @ConfiguredOption(key = "auth.passphrase"/* securitySensitive = true*/)
    Optional<char[]> authPassphrase();

    /**
     * The OCI authentication private key.
     * <p>
     * This configuration property has an effect only when {@code config} is, explicitly or implicitly,
     * present in the value for the {@link #authStrategies()}.
     * When it is present, this property must be provided in order to set the
     * {@linkplain ConfigFileAuthenticationDetailsProvider#getPrivateKey()}.
     *
     * @return the OCI authentication private key
     */
    @ConfiguredOption(key = "auth.private-key"/* securitySensitive = true*/)
    Optional<char[]> authPrivateKey();

    /**
     * The OCI region.
     * <p>
     * This configuration property has an effect only when {@code config} is, explicitly or implicitly,
     * present in the value for the {@link #authStrategies()}.
     * When it is present, either this property or {@link com.oracle.bmc.auth.RegionProvider} must be provide a value in order
     * to set the {@linkplain ConfigFileAuthenticationDetailsProvider#getRegion()}.
     *
     * @return the OCI region
     */
    @ConfiguredOption(key = "auth.region")
    Optional<String> authRegion();

    /**
     * The OCI tenant id.
     * <p>
     * This configuration property has an effect only when {@code config} is, explicitly or implicitly,
     * present in the value for the {@link #authStrategies()}.
     * When it is present, this property must be provided in order to set the
     * {@linkplain ConfigFileAuthenticationDetailsProvider#getTenantId()}.
     *
     * @return the OCI tenant id
     */
    @ConfiguredOption(key = "auth.tenant-id")
    Optional<String> authTenantId();

    /**
     * The OCI user id.
     * <p>
     * This configuration property has an effect only when {@code config} is, explicitly or implicitly,
     * present in the value for the {@link #authStrategies()}.
     * When it is present, this property must be provided in order to set the
     * {@linkplain ConfigFileAuthenticationDetailsProvider#getUserId()}.
     *
     * @return the OCI user id
     */
    @ConfiguredOption(key = "auth.user-id")
    Optional<String> authUserId();

    /**
     * The OCI idms primary hostname.
     * <p>
     * This configuration property is used to identify the metadata service url.
     *
     * @return the OCI idms primary hostname
     */
    @ConfiguredOption(value = PRIMARY_METADATA_SERVICE_BASE_URL, key = "idms.hostname")
    String idmsPrimaryHostName();

    /**
     * The OCI idms fallback hostname.
     * <p>
     * This configuration property is used to identify the fallback metadata service url.
     *
     * @return the OCI idms fallback hostname
     * @see OciAvailability
     */
    @ConfiguredOption(value = FALLBACK_METADATA_SERVICE_URL, key = "idms.fallback-hostname")
    Optional<String> idmsFallbackHostName();

    /**
     * The OCI idms connection timeout in millis. This is used to auto-detect availability.
     * <p>
     * This configuration property is used when attempting to connect to the metadata service.
     *
     * @return the OCI connection timeout in millis
     * @see OciAvailability
     */
    @ConfiguredOption(value = "100", key = "idms.timeout.milliseconds")
    int idmsTimeoutMilliseconds();

    /**
     * Determines whether there is sufficient configuration defined in this bean to be used for simple authentication.
     *
     * @return true if there is sufficient attributes defined for simple OCI authentication provider applicability
     * @see OciAuthenticationDetailsProvider
     */
    default boolean simpleConfigIsPresent() {
        return authRegion().isPresent()
                && authTenantId().isPresent()
                && authUserId().isPresent()
                && authPassphrase().isPresent()
                && authFingerprint().isPresent()
                && authPrivateKey().isPresent();

    }

}
