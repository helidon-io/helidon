/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.Supplier;

import io.helidon.common.LazyValue;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.inject.api.Bootstrap;
import io.helidon.inject.api.InjectionServices;
import io.helidon.inject.api.ServiceProvider;
import io.helidon.inject.api.Services;

import com.oracle.bmc.auth.AbstractAuthenticationDetailsProvider;

import static io.helidon.integrations.oci.sdk.runtime.OciAuthenticationDetailsProvider.AuthStrategy;
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
 * for <em>Helidon MP</em> integration. The implementation strategy, however, is
 * different between the two. Please take a moment to familiarize yourself to the
 * terminology and general approach before continuing further.
 * <p>
 * This module enables the
 * {@linkplain jakarta.inject.Inject injection} of any <em>service
 * interface</em>, <em>service client</em>, <em>service client
 * builder</em>, <em>asynchronous service interface</em>,
 * <em>asynchronous service client</em>, or <em>asynchronous service
 * client builder</em> from the <a
 * href="https://docs.oracle.com/en-us/iaas/tools/java/latest/index.html"
 * target="_top">Oracle Cloud Infrastructure Java SDK</a>.
 * <p>
 * Additionally, this module enables the {@linkplain jakarta.inject.Inject injection}
 * of the {@link com.oracle.bmc.auth.AbstractAuthenticationDetailsProvider},
 * which allows the corresponding service client to authenticate with the service.
 * <p>In all cases, user-supplied configuration will be preferred over any
 * default configuration. Please refer to {@link #ociConfig()} for details.
 *
 * <h2>Basic Usage</h2>
 *
 * To use this extension, make sure it is on your project's runtime
 * classpath. Also be sure the <em>helidon-integrations-oci-processor</em> is
 * on your APT/compile-time classpath. To {@linkplain jakarta.inject.Inject inject} a service
 * interface named
 * <code>com.oracle.bmc.</code><strong><code>cloudexample</code></strong><code>.CloudExample</code>
 * (or an analogous asynchronous service interface), you will also
 * need to ensure that its containing artifact is on your compile
 * classpath (e.g. <a
 * href="https://search.maven.org/search?q=oci-java-sdk-"
 * target="_top"><code>oci-java-sdk-</code><strong><code>cloudexample</code></strong><code>-$VERSION.jar</code></a>,
 * where {@code $VERSION} should be replaced by a suitable version
 * number).
 *
 * <h2>Advanced Usage</h2>
 *
 * <p>In the course of providing {@linkplain jakarta.inject.Inject
 * injection support} for a service interface or an asynchronous
 * service interface, this {@linkplain java.security.cert.Extension extension} will
 * create service client builder and asynchronous service client
 * builder instances by invoking the {@code static} {@code builder()}
 * method that is present on all OCI service client classes, and will then
 * provide those instances as regular Injection/Injectable services.  The resulting service client or
 * asynchronous service client will be built by that builder's {@link
 * com.oracle.bmc.common.ClientBuilderBase#build(AbstractAuthenticationDetailsProvider)
 * build(AbstractAuthenticationDetailsProvider)} method and will
 * itself be provided as a service instance.</p>
 *
 * <p>A user may wish to customize this builder so that the resulting
 * service client or asynchronous service client reflects the
 * customization.  She has two options:
 *
 * <ol>
 * <li>She may provide her own instance with the service client builder
 * type (or asynchronous client builder type).  In this case, the user
 * should supply an overriding (i.e., higher weighted) service provider
 * implementation than the one provided by {@link OciAuthenticationDetailsProvider}.
 *
 * <li>She may customize the service client builder (or asynchronous
 * service client builder) supplied by this {@link OciAuthenticationDetailsProvider}.
 * To do this, she must supply a custom configuration via {@link #ociConfig()}.
 * </ol>
 *
 * <h2>Configuration</h2>
 *
 * This extension uses the {@link OciConfig} for configuration. Refer to it
 * for details.
 *
 * @see <a
 * href="https://docs.oracle.com/en-us/iaas/tools/java/latest/index.html"
 * target="_top">Oracle Cloud Infrastructure Java SDK</a>
 *
 * @deprecated replaced with {@code helidon-integrations-oci} module
 */
@Deprecated(forRemoval = true, since = "4.1.0")
public final class OciExtension {
    /**
     * The name for the OCI bootstrap configuration file (value = {@value}).
     */
    static final String DEFAULT_OCI_GLOBAL_CONFIG_FILE = "oci.yaml";
    static final System.Logger LOGGER = System.getLogger(OciExtension.class.getName());
    static final LazyValue<OciConfig> DEFAULT_OCI_CONFIG_BEAN = LazyValue.create(() -> OciConfig.builder()
            .authStrategies(Arrays.stream(AuthStrategy.values())
                                    .filter(not(it -> it == AuthStrategy.AUTO))
                                    .map(AuthStrategy::id)
                                    .toList())
            .build());
    private static String overrideOciConfigFile;
    private static volatile Supplier<io.helidon.common.config.Config> ociConfigSupplier;
    private static volatile Supplier<io.helidon.common.config.Config> fallbackConfigSupplier;

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
     * If the implementation is unable to find this file, then a fallback mechanism will be used to find it in the configuration
     * found in the {@link InjectionServices#globalBootstrap()}, using a top-level attribute key named
     * {@value OciConfigBlueprint#CONFIG_KEY}.
     * <p>
     * The final fallback mechanism will use an {@code auto} authentication strategy - see {@link OciConfigBlueprint} for details.
     *
     * @return the bootstrap oci config bean
     * @see OciConfigBlueprint
     * @see #ociConfigSupplier
     */
    public static OciConfig ociConfig() {
        io.helidon.common.config.Config config = configSupplier().get();
        if (isSufficientlyConfigured(config)) {
            // we are good as-is
            return OciConfig.create(config);
        }

        // fallback
        config = InjectionServices.globalBootstrap()
                .flatMap(Bootstrap::config)
                .map(it -> it.get(OciConfig.CONFIG_KEY))
                .orElse(null);
        if (isSufficientlyConfigured(config)) {
            return OciConfig.create(config);
        }

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
        return () -> {
            Services services = InjectionServices.realizedServices();
            ServiceProvider<AbstractAuthenticationDetailsProvider> authProvider =
                    services.lookupFirst(AbstractAuthenticationDetailsProvider.class);
            return Objects.requireNonNull(authProvider.get());
        };
    }

    /**
     * The supplier for the raw config-backed by the OCI config source(s).
     *
     * @return the supplier for the raw config-backed by the OCI config source(s)
     * @see #configSupplier(Supplier)
     * @see #fallbackConfigSupplier(Supplier)
     * @see #ociAuthenticationProvider()
     */
    public static Supplier<io.helidon.common.config.Config> configSupplier() {
        if (ociConfigSupplier != null) {
            return ociConfigSupplier;
        }

        String ociConfigFile = ociConfigFilename();
        Path ociConfigFilePath = Paths.get(ociConfigFilename());
        boolean ociConfigResourceExists = (OciExtension.class.getClassLoader().getResource(ociConfigFile) != null);
        if (fallbackConfigSupplier != null
                && !(ociConfigResourceExists || Files.exists(ociConfigFilePath))) {
            return fallbackConfigSupplier;
        }

        configSupplier(() -> {
            // we do it this way to allow for any system and env vars to be used for the auth-strategy definition
            // (not advertised in the javadoc)
            return Config.create(
                    ConfigSources.classpath(ociConfigFile).optional(),
                    ConfigSources.file(ociConfigFilePath).optional());
        });

        return ociConfigSupplier;
    }

    /**
     * Establishes the supplier for the raw config-backed by the OCI config source(s). Setting this will override the usage of
     * the {@link #DEFAULT_OCI_GLOBAL_CONFIG_FILE} as the backing configuration file.
     *
     * @param configSupplier the config supplier
     * @see #configSupplier()
     */
    public static void configSupplier(Supplier<io.helidon.common.config.Config> configSupplier) {
        ociConfigSupplier = Objects.requireNonNull(configSupplier, "configSupplier");
    }

    /**
     * Establishes the fallback config supplier used only when the {@link #DEFAULT_OCI_GLOBAL_CONFIG_FILE} is not physically
     * present, and there has been no config supplier explicitly established via {@link #configSupplier(Supplier)}.
     * <p>
     * This method is typically used when running in CDI in order to allow for the fallback of using microprofile configuration.
     *
     * @param configSupplier the fallback config supplier
     * @see #configSupplier()
     */
    public static void fallbackConfigSupplier(Supplier<io.helidon.common.config.Config> configSupplier) {
        fallbackConfigSupplier = Objects.requireNonNull(configSupplier, "configSupplier");
    }

    /**
     * Returns {@code true} if the given config is sufficiently configured in order to identity an OCI authentication strategy.
     * If {@code false} then {@link OciAuthenticationDetailsProvider.AuthStrategy#AUTO} will be applied.
     *
     * @param config the config
     * @return true if the given config can be used to identify an OCI authentication strategy
     */
    public static boolean isSufficientlyConfigured(io.helidon.common.config.Config config) {
        return (config != null
                        && (config.get(KEY_AUTH_STRATEGY).exists()
                                    || config.get(KEY_AUTH_STRATEGIES).exists()));
    }

    // in support for testing a variant of oci.yaml
    static void ociConfigFileName(String fileName) {
        overrideOciConfigFile = fileName;
        ociConfigSupplier = null;
        fallbackConfigSupplier = null;
    }

    // in support for testing a variant of oci.yaml
    static String ociConfigFilename() {
        return (overrideOciConfigFile == null) ? DEFAULT_OCI_GLOBAL_CONFIG_FILE : overrideOciConfigFile;
    }

}
