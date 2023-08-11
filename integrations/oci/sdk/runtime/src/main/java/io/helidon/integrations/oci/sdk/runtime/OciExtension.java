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

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import io.helidon.common.LazyValue;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.inject.api.Bootstrap;
import io.helidon.inject.api.InjectionServices;
import io.helidon.inject.api.ServiceProvider;
import io.helidon.inject.api.Services;

import com.oracle.bmc.auth.AbstractAuthenticationDetailsProvider;

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
    private static Supplier<io.helidon.common.config.Config> ociConfigSupplier;

    private OciExtension() {
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
     */
    public static OciConfig ociConfig() {
        io.helidon.common.config.Config config = configSupplier().get();
        if (!config.exists()
                || !config.get(OciAuthenticationDetailsProvider.KEY_AUTH_STRATEGY).exists()) {
            // fallback
            Optional<Bootstrap> bootstrap = InjectionServices.globalBootstrap();
            if (bootstrap.isEmpty()) {
                LOGGER.log(System.Logger.Level.DEBUG, "No bootstrap - using default oci config");
                return DEFAULT_OCI_CONFIG_BEAN.get();
            }

            config = bootstrap.get().config().orElse(null);
            if (config == null) {
                LOGGER.log(System.Logger.Level.DEBUG, "No config in bootstrap - using default oci config");
                return DEFAULT_OCI_CONFIG_BEAN.get();
            }

            config = config.get(OciConfig.CONFIG_KEY);
            if (!config.exists()) {
                LOGGER.log(System.Logger.Level.DEBUG, "No oci config in bootstrap - using default oci config");
                return DEFAULT_OCI_CONFIG_BEAN.get();
            }
        }

        return OciConfig.create(config);
    }

    /**
     * The supplier for the raw config-backed by the OCI config source(s).
     *
     * @return the supplier for the raw config-backed by the OCI config source(s)
     */
    public static Supplier<io.helidon.common.config.Config> configSupplier() {
        if (ociConfigSupplier == null) {
            ociConfigSupplier = () -> {
                // we do it this way to allow for any system and env vars to be used for the auth-strategy definition
                // (not advertised in the javadoc)
                String ociConfigFile = ociConfigFilename();
                return Config.create(
                        ConfigSources.classpath(ociConfigFile).optional(),
                        ConfigSources.file(Paths.get(ociConfigFile)).optional());
            };
        }

        return ociConfigSupplier;
    }

    /**
     * The supplier for the globally configured authentication provider.
     *
     * @return the supplier for the globally configured authentication provider
     */
    public static Supplier<AbstractAuthenticationDetailsProvider> ociAuthenticationProvider() {
        return () -> {
            Services services = InjectionServices.realizedServices();
            ServiceProvider<AbstractAuthenticationDetailsProvider> authProvider =
                    services.lookupFirst(AbstractAuthenticationDetailsProvider.class);
            return Objects.requireNonNull(authProvider.get());
        };
    }

    static void ociConfigFileName(String fileName) {
        overrideOciConfigFile = fileName;
        ociConfigSupplier = null;
    }

    static String ociConfigFilename() {
        return (overrideOciConfigFile == null) ? DEFAULT_OCI_GLOBAL_CONFIG_FILE : overrideOciConfigFile;
    }

}
