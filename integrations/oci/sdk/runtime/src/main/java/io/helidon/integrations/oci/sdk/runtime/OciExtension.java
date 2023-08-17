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

import java.util.Arrays;
import java.util.Objects;
import java.util.function.Supplier;

import io.helidon.common.LazyValue;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;

import com.oracle.bmc.auth.AbstractAuthenticationDetailsProvider;

import static io.helidon.integrations.oci.sdk.runtime.OciAuthenticationDetailsProvider.KEY_AUTH_STRATEGIES;
import static io.helidon.integrations.oci.sdk.runtime.OciAuthenticationDetailsProvider.KEY_AUTH_STRATEGY;
import static io.helidon.integrations.oci.sdk.runtime.OciAuthenticationDetailsProvider.select;
import static java.util.function.Predicate.not;

/**
 * This class enables configuration access for integration to the <a
 * href="https://docs.oracle.com/en-us/iaas/tools/java/latest/index.html"
 * target="_top">Oracle Cloud Infrastructure Java SDK</a>. It is intended for
 * non-<em>Helidon MP</em>, non-CDI usage scenarios. For usages that involve
 * <em>Helidon MP</em> and CDI please refer to
 * {@code io.helidon.integrations.oci.sdk.cdi.OciExtension} instead. This
 * integration will follow the same terminology and usage pattern as specified
 * for <em>Helidon MP</em> integration.
 *
 * @see <a
 * href="https://docs.oracle.com/en-us/iaas/tools/java/latest/index.html"
 * target="_top">Oracle Cloud Infrastructure Java SDK</a>
 */
public final class OciExtension {
    static final String DEFAULT_OCI_GLOBAL_CONFIG_FILE = "oci.yaml";
    static final System.Logger LOGGER = System.getLogger(OciExtension.class.getName());
    static final LazyValue<OciConfig> DEFAULT_OCI_CONFIG_BEAN = LazyValue.create(() -> OciConfig.builder()
            .authStrategies(Arrays.stream(OciAuthenticationDetailsProvider.AuthStrategy.values())
                                    .filter(not(it -> it == OciAuthenticationDetailsProvider.AuthStrategy.AUTO))
                                    .map(OciAuthenticationDetailsProvider.AuthStrategy::id)
                                    .toList())
            .build());
    private static String overrideOciConfigFile;
    private static volatile Supplier<Config> ociConfigSupplier;

    private OciExtension() {
    }

    /**
     * The configured authentication provider strategy type name. Note, however, that the authentication strategy returned may not
     * necessarily be available. The configured authentication provider merely returns what is configured via
     * {@value OciAuthenticationDetailsProvider#KEY_AUTH_STRATEGY} and/or
     * {@value OciAuthenticationDetailsProvider#KEY_AUTH_STRATEGIES}. In order to additionally check if the provider is available,
     * the {@code verifyIsAvailable} argument should be {@code true}.
     *
     * @param verifyIsAvailable flag to indicate whether the provider should be checked for availability
     * @return the configured authentication type name
     */
    public static Class<? extends AbstractAuthenticationDetailsProvider>
    configuredAuthenticationDetailsProvider(boolean verifyIsAvailable) {
        return select(ociConfig(), verifyIsAvailable).authStrategy().type();
    }

    /**
     * Returns the global {@link OciConfig} bean that is currently defined in the bootstrap environment.
     * <p>
     * The implementation will first look for an {@code oci.yaml} file, and if found will use that file to establish the global
     * oci-specific bootstrap {@link io.helidon.config.spi.ConfigSource}.
     * <p>
     * The final fallback mechanism will use an {@code auto} authentication strategy - see {@link OciConfigBlueprint} for details.
     *
     * @return the bootstrap oci config bean
     * @see OciConfigBlueprint
     * @see #ociConfigSupplier
     */
    public static OciConfig ociConfig() {
        Config config = configSupplier().get();
        if (isSufficientlyConfigured(config)) {
            // we are good as-is
            return OciConfig.create(config);
        }

        // fallback
        LOGGER.log(System.Logger.Level.DEBUG, "No bootstrap - using default oci config");
        return DEFAULT_OCI_CONFIG_BEAN.get();
    }

    /**
     * The supplier for the globally configured OCI authentication provider.
     *
     * @return the supplier for the globally configured authentication provider
     * @see #configSupplier()
     */
    public static Supplier<? extends AbstractAuthenticationDetailsProvider> ociAuthenticationProvider() {
        // note that in v4 will use service registry, but here in v3 no extensibility is offered
        return () -> Objects.requireNonNull(new OciAuthenticationDetailsProvider().get());
    }

    /**
     * The supplier for the raw config-backed by the OCI config source(s).
     *
     * @return the supplier for the raw config-backed by the OCI config source(s)
     * @see #ociAuthenticationProvider()
     * @see #configSupplier(Supplier)
     */
    public static Supplier<Config> configSupplier() {
        if (ociConfigSupplier == null) {
            configSupplier(() -> {
                // we do it this way to allow for any system and env vars to be used for the auth-strategy definition
                // (not advertised in the javadoc)
                String ociConfigFile = ociConfigFilename();
                return Config.create(
                        ConfigSources.classpath(ociConfigFile).optional(),
                        ConfigSources.file(ociConfigFile).optional());
            });
        }

        return ociConfigSupplier;
    }

    /**
     * Establishes the supplier for the raw config-backed by the OCI config source(s).
     *
     * @param configSupplier the config supplier
     * @see #configSupplier()
     */
    public static void configSupplier(Supplier<Config> configSupplier) {
        ociConfigSupplier = configSupplier;
    }

    /**
     * Returns {@code true} if the given config is sufficiently configured in order to identity an OCI authentication strategy.
     * If {@code false} then {@link OciAuthenticationDetailsProvider.AuthStrategy#AUTO} will be applied.
     *
     * @param config the config
     * @return true if the given config can be used to identify an OCI authentication strategy
     */
    static boolean isSufficientlyConfigured(Config config) {
        return (config != null
                        && (config.get(KEY_AUTH_STRATEGY).exists()
                                    || config.get(KEY_AUTH_STRATEGIES).exists()));
    }

    // in support for testing a variant of oci.yaml
    static void ociConfigFileName(String fileName) {
        overrideOciConfigFile = fileName;
        ociConfigSupplier = null;
    }

    // in support for testing a variant of oci.yaml
    static String ociConfigFilename() {
        return (overrideOciConfigFile == null) ? DEFAULT_OCI_GLOBAL_CONFIG_FILE : overrideOciConfigFile;
    }

}
