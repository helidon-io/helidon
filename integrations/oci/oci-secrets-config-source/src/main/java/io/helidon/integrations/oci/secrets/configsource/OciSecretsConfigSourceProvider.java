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
package io.helidon.integrations.oci.secrets.configsource;

import java.lang.System.Logger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import io.helidon.common.LazyValue;
import io.helidon.config.AbstractConfigSource;
import io.helidon.config.AbstractConfigSourceBuilder;
import io.helidon.config.Config;
import io.helidon.config.spi.ConfigContent.NodeContent;
import io.helidon.config.spi.ConfigNode.ObjectNode;
import io.helidon.config.spi.ConfigNode.ValueNode;
import io.helidon.config.spi.ConfigSource;
import io.helidon.config.spi.ConfigSourceProvider;
import io.helidon.config.spi.NodeConfigSource;
import io.helidon.config.spi.PollableSource;
import io.helidon.config.spi.PollingStrategy;

import com.oracle.bmc.auth.BasicAuthenticationDetailsProvider;
import com.oracle.bmc.secrets.Secrets;
import com.oracle.bmc.secrets.SecretsClient;
import com.oracle.bmc.secrets.model.Base64SecretBundleContentDetails;
import com.oracle.bmc.secrets.model.SecretBundleContentDetails;
import com.oracle.bmc.secrets.requests.GetSecretBundleRequest;
import com.oracle.bmc.vault.Vaults;
import com.oracle.bmc.vault.VaultsClient;
import com.oracle.bmc.vault.model.SecretSummary;
import com.oracle.bmc.vault.requests.ListSecretsRequest;
import jakarta.annotation.Priority;

import static io.helidon.integrations.oci.sdk.runtime.OciExtension.ociAuthenticationProvider;
import static java.lang.System.Logger.Level.WARNING;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.time.Instant.now;
import static java.util.concurrent.Executors.newCachedThreadPool;

/**
 * A {@link ConfigSourceProvider} that {@linkplain #create(String, Config) creates} {@link ConfigSource} implementations
 * that interact with the Oracle Cloud Infrastructure (OCI) <a
 * href="https://docs.oracle.com/en-us/iaas/tools/java/latest/com/oracle/bmc/secrets/package-summary.html">Secrets
 * Retrieval</a> and <a
 * href="https://docs.oracle.com/en-us/iaas/tools/java/latest/com/oracle/bmc/vault/package-summary.html">Vault</a> APIs.
 *
 * <p>To use, ensure the packaging artifact (e.g. {@code .jar} file or similar) containing this class is present on your
 * class or module path as appropriate, and configure a meta-configuration source with a {@code type} of {@code
 * oci-secrets}, following the usual Helidon meta-configuration rules.</p>
 *
 * <p>More specifically:</p>
 *
 * <ol>
 *
 * <li>Ensure you have an authentication mechanism set up to connect to OCI (e.g. a valid <a
 * href="https://docs.oracle.com/en-us/iaas/Content/API/Concepts/sdkconfig.htm">OCI configuration
 * file</a>). Authentication with OCI is accomplished via the {@link
 * io.helidon.integrations.oci.sdk.runtime.OciExtension} class; please see its documentation for how and when to set up
 * an {@code oci.yaml} classpath resource to further refine the mechanism of authentication.</li>
 *
 * <li>Ensure there is a classpath resource present named {@code meta-config.yaml}.</li>
 *
 * <li>Ensure the {@code meta-config.yaml} classpath resource contains a {@code sources} element with a {@code type} of
 * {@code oci-secrets} that looks similar to the following, substituting values as appropriate:<blockquote><pre>sources:
 *  - type: 'oci-secrets'
 *    properties:
 *      compartment-ocid: 'your vault compartment OCID goes here'
 *      vault-ocid: 'your vault OCID goes here'</pre></blockquote></li>
 *
 * </ol>
 *
 * <p>Refer to Helidon's documentation concerning meta-configuration for more details.</p>
 *
 * @see ConfigSourceProvider
 */
@Priority(300)
public final class OciSecretsConfigSourceProvider implements ConfigSourceProvider {


    /*
     * Static fields.
     */


    private static final Set<String> SUPPORTED_TYPES = Set.of("oci-secrets");


    /*
     * Constructors.
     */


    /**
     * Creates a new {@link OciSecretsConfigSourceProvider}.
     *
     * @deprecated For use by {@link java.util.ServiceLoader} only.
     */
    @Deprecated // For use by java.util.ServiceLoader only.
    public OciSecretsConfigSourceProvider() {
        super();
    }


    /*
     * Instance methods.
     */


    /**
     * Creates and returns a non-{@code null} {@link ConfigSource} that sources its values from an Oracle Cloud
     * Infrastructure (OCI) <a
     * href="https://docs.oracle.com/en-us/iaas/Content/KeyManagement/Concepts/keyoverview.htm">Vault</a>.
     *
     * @param type one of the {@linkplain #supported() supported types}; not actually used
     *
     * @param metaConfig a {@link Config} serving as meta-configuration for this provider; must not be {@code null} when
     * {@code type} is {@linkplain #supports(String) supported}
     *
     * @return a non-{@code null} {@link ConfigSource}
     *
     * @exception NullPointerException if {@code type} is {@linkplain #supports(String) supported} and {@code
     * metaConfig} is {@code null}
     *
     * @see #supported()
     *
     * @deprecated For use by the Helidon Config subsystem only.
     */
    @Deprecated // For use by the Helidon Config subsystem only.
    @Override // ConfigSourceProvider
    public ConfigSource create(String type, Config metaConfig) {
        return SecretBundleConfigSource.builder().config(metaConfig).build();
    }

    /**
     * Returns a non-{@code null}, immutable {@link Set} of supported types suitable for the Helidon Config subsystem to
     * pass to the {@link #create(String, Config)} method.
     *
     * <p>This method returns a {@link Set} whose sole element is the string "{@code oci-secrets}".</p>
     *
     * @return a non-{@code null}, immutable {@link Set}
     *
     * @see #create(String, Config)
     *
     * @deprecated For use by the Helidon Config subsystem only.
     */
    @Deprecated // For use by the Helidon Config subsystem only.
    @Override // ConfigSourceProvider
    public Set<String> supported() {
        return SUPPORTED_TYPES;
    }

    /**
     * Returns {@code true} if and only if the {@link Set} returned by an invocation of the {@link #supported()} method
     * {@linkplain Set#contains(Object) contains} it.
     *
     * @param type the type to test
     *
     * @return {@code true} if and only if the {@link Set} returned by an invocation of the {@link #supported()} method
     * {@linkplain Set#contains(Object) contains} it
     *
     * @see #supported()
     *
     * @see #create(String, Config)
     *
     * @deprecated For use by the Helidon Config subsystem only.
     */
    @Deprecated // For use by the Helidon Config subsystem only.
    @Override // ConfigSourceProvider
    public boolean supports(String type) {
        return this.supported().contains(type);
    }


    /*
     * Inner and nested classes.
     */


    static final class SecretBundleConfigSource
        extends AbstractConfigSource implements NodeConfigSource, PollableSource<Instant> {


        /*
         * Static fields.
         */


        private static final Optional<NodeContent> ABSENT_NODE_CONTENT =
            Optional.of(NodeContent.builder().node(ObjectNode.empty()).build());

        private static final String COMPARTMENT_OCID_PROPERTY_NAME = "compartment-ocid";

        private static final Logger LOGGER = System.getLogger(SecretBundleConfigSource.class.getName());

        private static final String VAULT_OCID_PROPERTY_NAME = "vault-ocid";


        /*
         * Instance fields.
         */


        private final ExecutorService es;

        private final Supplier<? extends Optional<NodeContent>> loader;

        private volatile Instant closestSecretExpirationInstant;


        /*
         * Constructors.
         */


        private SecretBundleConfigSource(Builder b) {
            super(b);
            // From Executors#newCachedThreadPool() javadoc: "Creates a thread pool that creates new threads as needed,
            // but will reuse previously constructed threads when they are available. These pools will typically improve
            // the performance of programs that execute many short-lived asynchronous tasks." That describes our use
            // case exactly.
            this.es = newCachedThreadPool();
            // Helidon Config has no defined lifecycle so the best we can do is forcibly close the ExecutorService on VM
            // exit.
            Runtime.getRuntime().addShutdownHook(new Thread(() -> this.es.shutdownNow()));
            Supplier<? extends Secrets> secretsSupplier = Objects.requireNonNull(b.secretsSupplier, "b.secretsSupplier");
            Supplier<? extends Vaults> vaultsSupplier = Objects.requireNonNull(b.vaultsSupplier, "b.vaultsSupplier");
            this.closestSecretExpirationInstant = now();
            String compartmentOcid = b.compartmentOcid;
            String vaultOcid = b.vaultOcid;
            if (compartmentOcid == null || vaultOcid == null) {
                // (It is not immediately clear why the OCI Java SDK requires a Compartment OCID, since a Vault OCID is
                // sufficient to uniquely identify any Vault.)
                this.loader = this::absentNodeContent;
            } else {
                ListSecretsRequest listSecretsRequest = ListSecretsRequest.builder()
                    .compartmentId(compartmentOcid)
                    .vaultId(vaultOcid)
                    .build();
                this.loader = () -> this.load(vaultsSupplier, secretsSupplier, listSecretsRequest);
            }
        }


        /*
         * Instance methods.
         */


        @Deprecated // For use by the Helidon Config subsystem only.
        @Override // PollableSource
        public boolean isModified(Instant pollInstant) {
            return isModified(pollInstant, this.closestSecretExpirationInstant); // volatile read
        }

        @Deprecated // For use by the Helidon Config subsystem only.
        @Override // NodeConfigSource
        public Optional<NodeContent> load() {
            return this.loader.get();
        }

        @Deprecated // For use by the Helidon Config subsystem only.
        @Override // PollableSource
        public Optional<PollingStrategy> pollingStrategy() {
            return super.pollingStrategy();
        }

        private Optional<NodeContent> absentNodeContent() {
            return ABSENT_NODE_CONTENT;
        }

        private void completeTasks(Collection<? extends Callable<Void>> tasks, AutoCloseable autoCloseable) {
            try (autoCloseable) {
                completeTasks(this.es, tasks);
            } catch (RuntimeException e) {
                throw e;
            } catch (InterruptedException e) {
                // (Can legally be thrown by any AutoCloseable. Must preserve interrupt status.)
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e.getMessage(), e);
            } catch (Exception e) {
                // (Can legally be thrown by any AutoCloseable.)
                throw new IllegalStateException(e.getMessage(), e);
            }
        }

        private Optional<NodeContent> load(Supplier<? extends Vaults> vaultsSupplier,
                                           Supplier<? extends Secrets> secretsSupplier,
                                           ListSecretsRequest listSecretsRequest) {
            Collection<? extends SecretSummary> secretSummaries = secretSummaries(vaultsSupplier, listSecretsRequest);
            return this.load(secretSummaries, secretsSupplier);
        }

        private Optional<NodeContent> load(Collection<? extends SecretSummary> secretSummaries,
                                           Supplier<? extends Secrets> secretsSupplier) {
            if (secretSummaries.isEmpty()) {
                return this.absentNodeContent();
            }
            ConcurrentMap<String, ValueNode> valueNodes = new ConcurrentHashMap<>();
            Collection<Callable<Void>> tasks = new ArrayList<>(secretSummaries.size());
            Base64.Decoder decoder = Base64.getDecoder();
            Secrets secrets = secretsSupplier.get();
            Instant closestSecretExpirationInstant = this.closestSecretExpirationInstant; // volatile read
            for (SecretSummary ss : secretSummaries) {
                tasks.add(task(valueNodes::put,
                               ss.getSecretName(),
                               r -> secrets.getSecretBundle(r).getSecretBundle().getSecretBundleContent(),
                               ss.getId(),
                               decoder));
                java.util.Date d = ss.getTimeOfCurrentVersionExpiry();
                // If d is null, which is permitted by the OCI Vaults API, you could interpret it as meaning "this
                // secret never ever expires, so never poll it for changes ever again". (This is sort of like if its
                // expiration time were set to the end of time.)
                //
                // Or you could interpret it as the much more common "this secret never had its expiration time set,
                // probably by mistake, or because it's a temporary scratch secret, or any of a zillion other possible
                // common human explanations, so we'd better check each time we poll to see if the secret is still
                // there; i.e. we should pretend it is continually expiring". (This is sort of like if its expiration
                // time were set to the beginning of time.)
                //
                // We opt for the latter interpretation.
                Instant secretExpirationInstant = d == null ? null : d.toInstant();
                if (secretExpirationInstant != null && secretExpirationInstant.isBefore(closestSecretExpirationInstant)) {
                    closestSecretExpirationInstant = secretExpirationInstant;
                }
            }
            this.closestSecretExpirationInstant = closestSecretExpirationInstant; // volatile write
            this.completeTasks(tasks, secrets);
            ObjectNode.Builder onb = ObjectNode.builder();
            for (Entry<String, ValueNode> e : valueNodes.entrySet()) {
                onb.addValue(e.getKey(), e.getValue());
            }
            return Optional.of(NodeContent.builder()
                               .node(onb.build())
                               .build());
        }


        /*
         * Static methods.
         */


        private static Builder builder() {
            return new Builder();
        }

        private static void closeUnchecked(AutoCloseable autoCloseable) {
            try {
                autoCloseable.close();
            } catch (RuntimeException e) {
                throw e;
            } catch (InterruptedException e) {
                // (Can legally be thrown by any AutoCloseable. Must preserve interrupt status.)
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e.getMessage(), e);
            } catch (Exception e) {
                // (Can legally be thrown by any AutoCloseable.)
                throw new IllegalStateException(e.getMessage(), e);
            }
        }

        private static void completeTasks(ExecutorService es, Collection<? extends Callable<Void>> tasks) {
            RuntimeException re = null;
            for (Future<?> future : invokeAllUnchecked(es, tasks)) {
                try {
                    futureGetUnchecked(future);
                } catch (RuntimeException e) {
                    if (re == null) {
                        re = e;
                    } else {
                        re.addSuppressed(e);
                    }
                }
            }
            if (re != null) {
                throw re;
            }
        }

        private static <T> T futureGetUnchecked(Future<T> future) {
            try {
                return future.get();
            } catch (ExecutionException e) {
                throw new IllegalStateException(e.getMessage(), e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e.getMessage(), e);
            }
        }

        private static <T> List<Future<T>> invokeAllUnchecked(ExecutorService es, Collection<? extends Callable<T>> tasks) {
            try {
                return es.invokeAll(tasks);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e.getMessage(), e);
            }
        }

        static boolean isModified(Instant pollInstant, Instant closestSecretExpirationInstant) {
            return closestSecretExpirationInstant.isBefore(pollInstant);
        }

        private static GetSecretBundleRequest request(String secretId) {
            return GetSecretBundleRequest.builder().secretId(secretId).build();
        }

        // Suppress "[try] auto-closeable resource Vaults has a member method close() that could throw
        // InterruptedException" since we handle it.
        @SuppressWarnings("try")
        private static Collection<? extends SecretSummary> secretSummaries(Supplier<? extends Vaults> vaultsSupplier,
                                                                           ListSecretsRequest listSecretsRequest) {
            try (Vaults v = vaultsSupplier.get()) {
                return v.listSecrets(listSecretsRequest).getItems();
            } catch (RuntimeException e) {
                throw e;
            } catch (InterruptedException e) {
                // (Can legally be thrown by any AutoCloseable (such as Vaults). Must preserve interrupt status.)
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e.getMessage(), e);
            } catch (Exception e) {
                // (Can legally be thrown by any AutoCloseable (such as Vaults).)
                throw new IllegalStateException(e.getMessage(), e);
            }
        }

        static Callable<Void> task(BiConsumer<? super String, ? super ValueNode> valueNodes,
                                   String secretName,
                                   Function<? super GetSecretBundleRequest, ? extends SecretBundleContentDetails> f,
                                   String secretId,
                                   Base64.Decoder base64Decoder) {
            return () -> {
                valueNodes.accept(secretName, valueNode(f, secretId, base64Decoder));
                return null;
            };
        }

        private static ValueNode valueNode(Function<? super GetSecretBundleRequest, ? extends SecretBundleContentDetails> f,
                                           String secretId,
                                           Base64.Decoder base64Decoder) {
            return valueNode((Base64SecretBundleContentDetails) f.apply(request(secretId)), base64Decoder);
        }

        private static ValueNode valueNode(Base64SecretBundleContentDetails details, Base64.Decoder base64Decoder) {
            return valueNode(details.getContent(), base64Decoder);
        }

        static ValueNode valueNode(String base64EncodedContent, Base64.Decoder base64Decoder) {
            String decodedContent = new String(base64Decoder.decode(base64EncodedContent), UTF_8);
            return ValueNode.create(decodedContent.intern());
        }


        /*
         * Inner and nested classes.
         */


        private static final class Builder extends AbstractConfigSourceBuilder<Builder, Void> {


            /*
             * Instance fields.
             */


            private String compartmentOcid;

            private Supplier<? extends Secrets> secretsSupplier;

            private String vaultOcid;

            private Supplier<? extends Vaults> vaultsSupplier;


            /*
             * Constructors.
             */


            private Builder() {
                super();
                Supplier<? extends BasicAuthenticationDetailsProvider> adpSupplier =
                    LazyValue.create(() -> (BasicAuthenticationDetailsProvider) ociAuthenticationProvider().get());
                SecretsClient.Builder scb = SecretsClient.builder();
                this.secretsSupplier = () -> scb.build(adpSupplier.get());
                VaultsClient.Builder vcb = VaultsClient.builder();
                this.vaultsSupplier = () -> vcb.build(adpSupplier.get());
            }


            /*
             * Instance methods.
             */


            @Override // AbstractConfigSourceBuilder<Builder, Void>
            protected Builder config(Config metaConfig) {
                metaConfig.get("compartment-ocid")
                    .asString()
                    .filter(Predicate.not(String::isBlank))
                    .ifPresentOrElse(this::compartmentOcid,
                                     () -> {
                                         if (LOGGER.isLoggable(WARNING)) {
                                             LOGGER.log(WARNING,
                                                        "No meta-configuration value supplied for "
                                                        + metaConfig.key().toString() + "." + COMPARTMENT_OCID_PROPERTY_NAME
                                                        + "); resulting ConfigSource will be empty");
                                         }
                                     });
                metaConfig.get("vault-ocid")
                    .asString()
                    .filter(Predicate.not(String::isBlank))
                    .ifPresentOrElse(this::vaultOcid,
                                     () -> {
                                         if (LOGGER.isLoggable(WARNING)) {
                                             LOGGER.log(WARNING,
                                                        "No meta-configuration value supplied for "
                                                        + metaConfig.key().toString() + "." + VAULT_OCID_PROPERTY_NAME
                                                        + "); resulting ConfigSource will be empty");
                                         }
                                     });
                return super.config(metaConfig);
            }

            private SecretBundleConfigSource build() {
                return new SecretBundleConfigSource(this);
            }

            private Builder compartmentOcid(String compartmentOcid) {
                this.compartmentOcid = Objects.requireNonNull(compartmentOcid, "compartmentOcid");
                return this;
            }

            private Builder secretsSupplier(Supplier<? extends Secrets> secretsSupplier) {
                this.secretsSupplier = Objects.requireNonNull(secretsSupplier, "secretsSupplier");
                return this;
            }

            private Builder vaultOcid(String vaultOcid) {
                this.vaultOcid = Objects.requireNonNull(vaultOcid, "vaultOcid");
                return this;
            }

            private Builder vaultsSupplier(Supplier<? extends Vaults> vaultsSupplier) {
                this.vaultsSupplier = Objects.requireNonNull(vaultsSupplier, "vaultsSupplier");
                return this;
            }

        }

    }

}
