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

package io.helidon.integrations.oci.sdk.runtime;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import io.helidon.builder.api.Prototype;
import io.helidon.config.metadata.Configured;
import io.helidon.config.metadata.ConfiguredOption;
import io.helidon.config.metadata.ConfiguredValue;

import static io.helidon.integrations.oci.sdk.runtime.OciAuthenticationDetailsProvider.ALL_STRATEGIES;
import static io.helidon.integrations.oci.sdk.runtime.OciAuthenticationDetailsProvider.TAG_AUTO;
import static io.helidon.integrations.oci.sdk.runtime.OciAuthenticationDetailsProvider.TAG_CONFIG;
import static io.helidon.integrations.oci.sdk.runtime.OciAuthenticationDetailsProvider.TAG_CONFIG_FILE;
import static io.helidon.integrations.oci.sdk.runtime.OciAuthenticationDetailsProvider.TAG_INSTANCE_PRINCIPALS;
import static io.helidon.integrations.oci.sdk.runtime.OciAuthenticationDetailsProvider.TAG_RESOURCE_PRINCIPALS;

/**
 * Configuration used by {@link OciAuthenticationDetailsProvider}.
 *
 * @see OciExtension
 */
// note: this is intended to be a replica to the properties carried from the cdi integrations previously done for MP
@Prototype.Blueprint
@Configured(root = true, prefix = OciConfigBlueprint.CONFIG_KEY)
interface OciConfigBlueprint {
    /**
     * Config key of this config.
     */
    String CONFIG_KEY = "oci";
    /**
     * Primary hostname of metadata service.
     */
    String IMDS_HOSTNAME = "169.254.169.254";
    /**
     * Redefine the constant, as it is private in BMC.
     */
    String DEFAULT_PROFILE_NAME = "DEFAULT";

    /**
     * The singular authentication strategy to apply. This will be preferred over {@link #authStrategies()} if both are
     * present.
     *
     * @return the singular authentication strategy to be applied
     */
    @ConfiguredOption(allowedValues = {
            @ConfiguredValue(value = TAG_AUTO, description = "auto select first applicable"),
            @ConfiguredValue(value = TAG_CONFIG, description = "simple authentication provider"),
            @ConfiguredValue(value = TAG_CONFIG_FILE, description = "config file authentication provider"),
            @ConfiguredValue(value = TAG_INSTANCE_PRINCIPALS, description = "instance principals authentication provider"),
            @ConfiguredValue(value = TAG_RESOURCE_PRINCIPALS, description = "resource principals authentication provider"),
    })
    Optional<String> authStrategy();

    /**
     * The list of authentication strategies that will be attempted by
     * {@link com.oracle.bmc.auth.AbstractAuthenticationDetailsProvider} when one is
     * called for. This is only used if {@link #authStrategy()} is not present.
     *
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
     * @return the list of authentication strategies that will be applied, defaulting to {@code auto}
     * @see io.helidon.integrations.oci.sdk.runtime.OciAuthenticationDetailsProvider.AuthStrategy
     */
    @ConfiguredOption(allowedValues = {
            @ConfiguredValue(value = TAG_AUTO, description = "auto select first applicable"),
            @ConfiguredValue(value = TAG_CONFIG, description = "simple authentication provider"),
            @ConfiguredValue(value = TAG_CONFIG_FILE, description = "config file authentication provider"),
            @ConfiguredValue(value = TAG_INSTANCE_PRINCIPALS, description = "instance principals authentication provider"),
            @ConfiguredValue(value = TAG_RESOURCE_PRINCIPALS, description = "resource principals authentication provider"),
    })
    List<String> authStrategies();

    /**
     * The OCI configuration profile path.
     * <p>
     * This configuration property has an effect only when {@code config-file} is, explicitly or implicitly,
     * present in the value for the {@link #authStrategies()}. This is also known as {@link #fileConfigIsPresent()}.
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
     * present in the value for the {@link #authStrategies()}. This is also known as {@link #fileConfigIsPresent()}.
     * When it is present, this property may also be optionally provided in order to override the default
     * {@value #DEFAULT_PROFILE_NAME}.
     *
     * @return the optional OCI configuration/auth profile name
     */
    @ConfiguredOption(value = DEFAULT_PROFILE_NAME, key = "config.profile")
    Optional<String> configProfile();

    /**
     * The OCI authentication fingerprint.
     * <p>
     * This configuration property has an effect only when {@code config} is, explicitly or implicitly,
     * present in the value for the {@link #authStrategies()}. This is also known as {@link #simpleConfigIsPresent()}.
     * When it is present, this property must be provided in order to set the <a
     * href="https://docs.oracle.com/en-us/iaas/Content/API/Concepts/apisigningkey.htm">API signing key's fingerprint</a>.
     * See {@linkplain com.oracle.bmc.auth.SimpleAuthenticationDetailsProvider#getFingerprint()} for more details.
     *
     * @return the OCI authentication fingerprint
     */
    @ConfiguredOption(key = "auth.fingerprint")
    Optional<String> authFingerprint();

    /**
     * The OCI authentication key file.
     * <p>
     * This configuration property has an effect only when {@code config} is, explicitly or implicitly,
     * present in the value for the {@link #authStrategies()}. This is also known as {@link #simpleConfigIsPresent()}.
     * When it is present, this property must be provided in order to set the
     * {@linkplain com.oracle.bmc.auth.SimpleAuthenticationDetailsProvider#getPrivateKey()}. This file must exist in the
     * {@code user.home} directory. Alternatively, this property can be set using either {@link #authPrivateKey()} or
     * using {@link #authPrivateKeyPath()}.
     *
     * @return the OCI authentication key file
     */
    @ConfiguredOption(value = "oci_api_key.pem", key = "auth.keyFile")
    String authKeyFile();

    /**
     * The OCI authentication key file path.
     * <p>
     * This configuration property has an effect only when {@code config} is, explicitly or implicitly,
     * present in the value for the {@link #authStrategies()}. This is also known as {@link #simpleConfigIsPresent()}.
     * When it is present, this property must be provided in order to set the
     * {@linkplain com.oracle.bmc.auth.SimpleAuthenticationDetailsProvider#getPrivateKey()}. This file path is
     * an alternative for using {@link #authKeyFile()} where the file must exist in the {@code user.home} directory.
     * Alternatively, this property can be set using {@link #authPrivateKey()}.
     *
     * @return the OCI authentication key file path
     */
    @ConfiguredOption(key = "auth.private-key-path")
    Optional<String> authPrivateKeyPath();

    /**
     * The OCI authentication private key.
     * <p>
     * This configuration property has an effect only when {@code config} is, explicitly or implicitly,
     * present in the value for the {@link #authStrategies()}. This is also known as {@link #simpleConfigIsPresent()}.
     * When it is present, this property must be provided in order to set the
     * {@linkplain com.oracle.bmc.auth.SimpleAuthenticationDetailsProvider#getPrivateKey()}. Alternatively, this property
     * can be set using either {@link #authKeyFile()} residing in the {@code user.home} directory, or using
     * {@link #authPrivateKeyPath()}.
     *
     * @return the OCI authentication private key
     */
    // See https://github.com/helidon-io/helidon/issues/6908
    @ConfiguredOption(key = "auth.private-key")
    @Prototype.Confidential
    Optional<char[]> authPrivateKey();

    /**
     * The OCI authentication passphrase.
     * <p>
     * This configuration property has an effect only when {@code config} is, explicitly or implicitly,
     * present in the value for the {@link #authStrategies()}. This is also known as {@link #simpleConfigIsPresent()}.
     * When it is present, this property must be provided in order to set the
     * {@linkplain com.oracle.bmc.auth.SimpleAuthenticationDetailsProvider#getPassphraseCharacters()}.
     *
     * @return the OCI authentication passphrase
     */
    // See https://github.com/helidon-io/helidon/issues/6908
    @ConfiguredOption(key = "auth.passphrase")
    @Prototype.Confidential
    Optional<char[]> authPassphrase();

    /**
     * The OCI region.
     * <p>
     * This configuration property has an effect only when {@code config} is, explicitly or implicitly,
     * present in the value for the {@link #authStrategies()}. This is also known as {@link #simpleConfigIsPresent()}.
     * When it is present, either this property or {@link com.oracle.bmc.auth.RegionProvider} must be provide a value in order
     * to set the {@linkplain com.oracle.bmc.auth.ConfigFileAuthenticationDetailsProvider#getRegion()}.
     *
     * @return the OCI region
     */
    @ConfiguredOption(key = "auth.region")
    Optional<String> authRegion();

    /**
     * The OCI tenant id.
     * <p>
     * This configuration property has an effect only when {@code config} is, explicitly or implicitly,
     * present in the value for the {@link #authStrategies()}. This is also known as {@link #simpleConfigIsPresent()}.
     * When it is present, this property must be provided in order to set the
     * {@linkplain com.oracle.bmc.auth.ConfigFileAuthenticationDetailsProvider#getTenantId()}.
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
     * {@linkplain com.oracle.bmc.auth.ConfigFileAuthenticationDetailsProvider#getUserId()}.
     *
     * @return the OCI user id
     */
    @ConfiguredOption(key = "auth.user-id")
    Optional<String> authUserId();

    /**
     * The OCI IMDS hostname.
     * <p>
     * This configuration property is used to identify the metadata service url.
     *
     * @return the OCI IMDS hostname
     */
    @ConfiguredOption(value = IMDS_HOSTNAME, key = "imds.hostname")
    String imdsHostName();

    /**
     * The OCI IMDS connection timeout. This is used to auto-detect availability.
     * <p>
     * This configuration property is used when attempting to connect to the metadata service.
     *
     * @return the OCI IMDS connection timeout
     * @see OciAvailability
     */
    @ConfiguredOption(value = "PT0.1S", key = "imds.timeout.milliseconds")
    Duration imdsTimeout();

    /**
     * The list of {@link io.helidon.integrations.oci.sdk.runtime.OciAuthenticationDetailsProvider.AuthStrategy} names (excluding {@link io.helidon.integrations.oci.sdk.runtime.OciAuthenticationDetailsProvider.AuthStrategy#AUTO}) that
     * are potentially applicable for use. Here, "potentially applicable for use" means that it is set using the
     * {@link #authStrategy()} attribute on this config bean. If not present then the fall-back looks to use the values
     * explicitly or implicitly set by {@link #authStrategies()}.
     *
     * @return the list of potential auth strategies that are applicable
     */
    default List<String> potentialAuthStrategies() {
        String authStrategy = authStrategy().orElse(null);
        if (authStrategy != null
                && !TAG_AUTO.equalsIgnoreCase(authStrategy)
                && !authStrategy.isBlank()) {
            if (!ALL_STRATEGIES.contains(authStrategy)) {
                throw new IllegalStateException("Unknown auth strategy: " + authStrategy);
            }

            return List.of(authStrategy);
        }

        List<String> result = new ArrayList<>();
        authStrategies().stream()
                .map(String::trim)
                .filter(Predicate.not(String::isBlank))
                .forEach(s -> {
                    if (!ALL_STRATEGIES.contains(s) && !TAG_AUTO.equals(s)) {
                        throw new IllegalStateException("Unknown auth strategy: " + s);
                    }
                    result.add(s);
                });
        if (result.isEmpty() || result.contains(TAG_AUTO)) {
            return ALL_STRATEGIES;
        }

        return result;
    }

    /**
     * Determines whether sufficient configuration is present on this bean to be used for OCI's "file-based" authentication
     * provider. This matches to the {@link io.helidon.integrations.oci.sdk.runtime.OciAuthenticationDetailsProvider.AuthStrategy#CONFIG_FILE}.
     *
     * @return true if there is sufficient attributes defined for file-based OCI authentication provider applicability
     * @see OciAuthenticationDetailsProvider
     */
    default boolean fileConfigIsPresent() {
        return configPath().isPresent()
                && !configPath().orElseThrow().isBlank()
                && configProfile().isPresent()
                && !configProfile().orElseThrow().isBlank();
    }

    /**
     * Determines whether sufficient configuration is present on this bean to be used for OCI's "simple" authentication provider.
     * This matches to the {@link io.helidon.integrations.oci.sdk.runtime.OciAuthenticationDetailsProvider.AuthStrategy#CONFIG}.
     *
     * @return true if there is sufficient attributes defined for simple OCI authentication provider applicability
     * @see OciAuthenticationDetailsProvider
     */
    default boolean simpleConfigIsPresent() {
        return authTenantId().isPresent()
                && authUserId().isPresent()
                && authPassphrase().isPresent()
                && authFingerprint().isPresent()
                // don't test region since it can alternatively come from the region provider
                //                && authRegion().isPresent()
                && (
                authPrivateKey().isPresent()
                        || authPrivateKeyPath().isPresent());
    }

}
