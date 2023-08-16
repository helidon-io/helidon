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
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import io.helidon.config.metadata.ConfiguredOption;
import io.helidon.config.metadata.ConfiguredValue;

import com.oracle.bmc.ConfigFileReader;

import static io.helidon.integrations.oci.sdk.runtime.OciAuthenticationDetailsProvider.ALL_STRATEGIES;
import static io.helidon.integrations.oci.sdk.runtime.OciAuthenticationDetailsProvider.VAL_AUTO;
import static io.helidon.integrations.oci.sdk.runtime.OciAuthenticationDetailsProvider.VAL_CONFIG;
import static io.helidon.integrations.oci.sdk.runtime.OciAuthenticationDetailsProvider.VAL_CONFIG_FILE;
import static io.helidon.integrations.oci.sdk.runtime.OciAuthenticationDetailsProvider.VAL_INSTANCE_PRINCIPALS;
import static io.helidon.integrations.oci.sdk.runtime.OciAuthenticationDetailsProvider.VAL_RESOURCE_PRINCIPAL;

/**
 * Configuration used by {@link OciAuthenticationDetailsProvider}.
 * <p>
 * Access the global {@link OciConfig} using the {@link OciExtension#ociConfig()} method.
 * The configuration for this is delivered via a special {@value OciExtension#DEFAULT_OCI_GLOBAL_CONFIG_FILE} file. Minimally,
 * this configuration file should have a key named {@value OciAuthenticationDetailsProvider#KEY_AUTH_STRATEGY} or else a
 * list of auth strategies having a key named {@value OciAuthenticationDetailsProvider#KEY_AUTH_STRATEGIES}. In the later, all
 * of the named auth strategies will be checked in the order they were specified for availability in the runtime environment (see
 * details below). Here is an example for what the configuration would look like when a single auth strategy is explicitly
 * configured :
 * <pre>
 *     # oci.yaml
 *     auth-strategy : "config"
 * </pre>
 * And here is another example when the runtime should search true multi auth strategies in order to select the first one
 * available in the runtime environment:
 * <pre>
 *     # oci.yaml
 *     # if instance-principals are available then use it, going down the chain checking for availability, etc.
 *     auth-strategies: "instance-principals, config-file, resource-principal, config"
 * </pre>
 *
 * <p>
 * Each configured {@link OciAuthenticationDetailsProvider#KEY_AUTH_STRATEGY} has varying constraints:
 * <ul>
 *     <li>instance-principals - the JVM must be able to detect that it is running on a OCI compute node instance.</li>
 *     <li>resource-principal - the env variable {@value OciAuthenticationDetailsProvider#TAG_RESOURCE_PRINCIPAL_VERSION} is
 *     required to be set in the runtime environment.</li>
 *     <li>config-file - the {@code $HOME/.oci/config} is available on the file system. This configuration also allows for the
 *     optional key named {@code config-profile} to be used to override the file location in the runtime environment.</li>
 *     <li>config - this configuration allows for these additional values to be set: {@code auth-tenant-id},
 *     {@code auth-user-id}, {@code auth-region}, {@code auth-fingerprint}, {@code auth-passphrase()}, and
 *     {@code auth-private-key}. Note that this configuration is only recommended in a development (i.e., non-production)
 *     environment since it relies on these additional security-sensitive values to be set. Note that these values cannot be
 *     sourced out of the Vault since this configuration source is primordial - the vault is not accessible here.</li>
 * </ul>
 * See {@link #authStrategies()} for additional details.
 * <p>
 * The default value for {@link OciAuthenticationDetailsProvider#KEY_AUTH_STRATEGY} is set to {@code auto}, meaning that
 * the authentication strategy will follow a search heuristic to determine the appropriate setting. When running in the OCI
 * runtime environment (i.e., the JVM is running on a detectable OCI compute node instance) then {@code instance-principals}
 * is used, with a final fallback set to be {@code config-file} (i.e., $HOME/.oci/config).
 *
 * @see OciExtension
 */
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
            @ConfiguredValue(value = VAL_AUTO, description = "auto select first applicable"),
            @ConfiguredValue(value = VAL_CONFIG, description = "simple authentication provider"),
            @ConfiguredValue(value = VAL_CONFIG_FILE, description = "config file authentication provider"),
            @ConfiguredValue(value = VAL_INSTANCE_PRINCIPALS, description = "instance principals authentication provider"),
            @ConfiguredValue(value = VAL_RESOURCE_PRINCIPAL, description = "resource principals authentication provider"),
    })
    Optional<String> authStrategy();

    /**
     * The list of authentication strategies that will be attempted by
     * {@link com.oracle.bmc.auth.AbstractAuthenticationDetailsProvider} when one is
     * called for. This is only used if {@link #authStrategy()} is not present.
     *
     * <ul>
     *      <li>{@code auto} - if present in the list, or if no value
     *          for this property exists.</li>
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
     * If there are more than one strategy descriptors defined, the
     * first one that is deemed to be available/suitable will be used and all others will be ignored.
     *
     * @return the list of authentication strategies that will be applied, defaulting to {@code auto}
     * @see io.helidon.integrations.oci.sdk.runtime.OciAuthenticationDetailsProvider.AuthStrategy
     */
    @ConfiguredOption(allowedValues = {
            @ConfiguredValue(value = VAL_AUTO, description = "auto select first applicable"),
            @ConfiguredValue(value = VAL_CONFIG, description = "simple authentication provider"),
            @ConfiguredValue(value = VAL_CONFIG_FILE, description = "config file authentication provider"),
            @ConfiguredValue(value = VAL_INSTANCE_PRINCIPALS, description = "instance principals authentication provider"),
            @ConfiguredValue(value = VAL_RESOURCE_PRINCIPAL, description = "resource principal authentication provider"),
    })
    List<String> authStrategies();

    /**
     * The OCI configuration profile path.
     * <p>
     * This configuration property has an effect only when {@code config-file} is, explicitly or implicitly,
     * present in the value for the {@link #authStrategies()}. This is also known as {@link #fileConfigIsPresent()}.
     * When it is present, this property must also be present and then the
     * {@linkplain ConfigFileReader#parse(String)}
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
//    @Prototype.Confidential
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
//    @Prototype.Confidential
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
     */
    @ConfiguredOption(value = "PT0.1S", key = "imds.timeout.milliseconds")
    Duration imdsTimeout();

    /**
     * The list of {@link io.helidon.integrations.oci.sdk.runtime.OciAuthenticationDetailsProvider.AuthStrategy} names
     * (excluding {@link io.helidon.integrations.oci.sdk.runtime.OciAuthenticationDetailsProvider.AuthStrategy#AUTO}) that
     * are potentially applicable for use. Here, "potentially applicable for use" means that it is set using the
     * {@link #authStrategy()} attribute on this config bean. If not present then the fall-back looks to use the values
     * explicitly or implicitly set by {@link #authStrategies()}. Note that the order of this list is important as it pertains
     * to the search/strategy ordering.
     *
     * @return the list of potential auth strategies that are applicable
     */
    default List<String> potentialAuthStrategies() {
        String authStrategy = authStrategy().orElse(null);
        if (authStrategy != null
                && !VAL_AUTO.equalsIgnoreCase(authStrategy)
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
                    if (!ALL_STRATEGIES.contains(s) && !VAL_AUTO.equals(s)) {
                        throw new IllegalStateException("Unknown auth strategy: " + s);
                    }
                    result.add(s);
                });
        if (result.isEmpty() || result.contains(VAL_AUTO)) {
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
     * @see com.oracle.bmc.auth.ConfigFileAuthenticationDetailsProvider
     */
    default boolean fileConfigIsPresent() {
        // the implementation will use ConfigFileAuthenticationDetailsProvider(ConfigFileReader.parseDefault()), so we are
        // therefore relaxing the criteria here for matching, and instead just verifying that parseDefaults will "work" later
        // as the fallback mechanism.
        if ((configPath().isPresent() && !configProfile().get().isBlank())
                || (configProfile().isPresent() && configProfile().get().isBlank())) {
            return true;
        }

        try {
            ConfigFileReader.ConfigFile ignoredCfgFile = ConfigFileReader.parseDefault();
            Objects.requireNonNull(ignoredCfgFile);
            return true;
        } catch (Exception e) {
            OciAuthenticationDetailsProvider.LOGGER.log(System.Logger.Level.DEBUG,
                                                        "file config is not available: " + e.getMessage(), e);
            return false;
        }
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
                && (authPrivateKey().isPresent()
                        || authPrivateKeyPath().isPresent());
    }

}
