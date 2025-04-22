/*
 * Copyright (c) 2023, 2025 Oracle and/or its affiliates.
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
import java.util.Collections;
import java.util.EnumSet;
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
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import io.helidon.config.AbstractConfigSource;
import io.helidon.config.AbstractConfigSourceBuilder;
import io.helidon.config.Config;
import io.helidon.config.spi.ConfigContent.NodeContent;
import io.helidon.config.spi.ConfigNode.ObjectNode;
import io.helidon.config.spi.ConfigNode.ValueNode;
import io.helidon.config.spi.NodeConfigSource;
import io.helidon.config.spi.PollableSource;
import io.helidon.config.spi.PollingStrategy;

import com.oracle.bmc.secrets.Secrets;
import com.oracle.bmc.secrets.model.Base64SecretBundleContentDetails;
import com.oracle.bmc.secrets.model.SecretBundle;
import com.oracle.bmc.secrets.requests.GetSecretBundleRequest;
import com.oracle.bmc.secrets.responses.GetSecretBundleResponse;
import com.oracle.bmc.vault.Vaults;
import com.oracle.bmc.vault.VaultsClient;
import com.oracle.bmc.vault.model.SecretSummary;
import com.oracle.bmc.vault.model.SecretSummary.LifecycleState;
import com.oracle.bmc.vault.requests.ListSecretsRequest;

// import static com.oracle.bmc.secrets.requests.GetSecretBundleRequest.Stage.Current;
import static com.oracle.bmc.vault.model.SecretSummary.LifecycleState.Active;
import static com.oracle.bmc.vault.model.SecretSummary.LifecycleState.CancellingDeletion;
import static com.oracle.bmc.vault.model.SecretSummary.LifecycleState.Updating;
import static java.lang.System.Logger.Level.WARNING;
import static java.time.Instant.now;
import static java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor;

/**
 * An {@link AbstractConfigSource}, {@link NodeConfigSource} and {@link PollableSource} implementation that sources its
 * values from the Oracle Cloud Infrastructure (OCI) <a
 * href="https://docs.oracle.com/en-us/iaas/tools/java/latest/com/oracle/bmc/secrets/package-summary.html">Secrets
 * Retrieval</a> and <a
 * href="https://docs.oracle.com/en-us/iaas/tools/java/latest/com/oracle/bmc/vault/package-summary.html">Vault</a> APIs.
 */
public final class SecretBundleNodeConfigSource
        extends AbstractSecretBundleConfigSource<SecretBundleNodeConfigSource.Builder>
        implements NodeConfigSource, PollableSource<SecretBundleNodeConfigSource.Stamp> {

    private static final String COMPARTMENT_OCID_PROPERTY_NAME = "compartment-ocid";
    private static final Logger LOGGER = System.getLogger(SecretBundleNodeConfigSource.class.getName());
    // Lifecycle states that a SecretSummary can have that indicate the secret version it represents either exists or
    // will continue to exist.
    private static final Collection<? extends LifecycleState> EXTANT_STATES =
        Collections.unmodifiableCollection(EnumSet.of(Active, CancellingDeletion, Updating));

    private final Supplier<Optional<NodeContent>> loader;
    private final Supplier<Stamp> stamper;

    private SecretBundleNodeConfigSource(Builder b) {
        super(b);
        Supplier<? extends Secrets> secretsSupplier = Objects.requireNonNull(b.secretsSupplier(), "b.secretsSupplier()");
        Supplier<? extends Vaults> vaultsSupplier = Objects.requireNonNull(b.vaultsSupplier, "b.vaultsSupplier");
        String vaultOcid = b.vaultOcid();
        if (b.compartmentOcid == null || vaultOcid == null) {
            this.loader = Optional::empty;
            this.stamper = Stamp::new;
        } else {
            ListSecretsRequest listSecretsRequest = ListSecretsRequest.builder()
                    .compartmentId(b.compartmentOcid)
                    .vaultId(vaultOcid)
                    .build();
            this.loader = () -> this.load(vaultsSupplier, secretsSupplier, listSecretsRequest);
            this.stamper = () -> toStamp(secretSummaries(vaultsSupplier, listSecretsRequest), secretsSupplier);
        }
    }


    /*
     * Instance methods.
     */

    /**
     * Creates and returns a new {@link Builder} for {@linkplain Builder#build() building} {@link
     * SecretBundleNodeConfigSource} instances.
     *
     * @return a new {@link Builder}
     */
    public static Builder builder() {
        return new Builder();
    }

    static Stamp toStamp(Collection<SecretSummary> secretSummaries, Set<String> eTags) {
        if (secretSummaries.isEmpty()) {
            return new Stamp();
        }
        Instant earliestExpiration = null;
        for (SecretSummary ss : secretSummaries) {
            if (ss.getLifecycleState() == Active) {
                java.util.Date d = ss.getTimeOfCurrentVersionExpiry();
                if (d == null) {
                    d = ss.getTimeOfDeletion();
                }
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
                if (d != null) {
                    Instant i = d.toInstant();
                    if (earliestExpiration == null || i.isBefore(earliestExpiration)) {
                        earliestExpiration = i;
                    }
                }
            }
        }
        return new Stamp(Set.copyOf(eTags), earliestExpiration == null ? now() : earliestExpiration);
    }

    static boolean isModified(Stamp pollStamp, Stamp stamp) {
        return
                !pollStamp.eTags().equals(stamp.eTags())
                        || stamp.earliestExpiration().isBefore(pollStamp.earliestExpiration());
    }

    static Callable<Void> task(BiConsumer<String, ValueNode> valueNodes,
                               Consumer<String> eTags,
                               String secretName,
                               Function<GetSecretBundleRequest, GetSecretBundleResponse> f,
                               String secretId,
                               Base64.Decoder base64Decoder) {
        return () -> {
            valueNodes.accept(secretName,
                              valueNode(request -> secretBundleContentDetails(request, f, eTags),
                                        secretId,
                                        base64Decoder));
            return null; // Callable<Void>; null is the only possible return value
        };
    }

    /**
     * Returns {@code true} if the values in this {@link SecretBundleNodeConfigSource} have been modified.
     *
     * @param lastKnownStamp a {@link Stamp}
     * @return {@code true} if modified
     */
    @Deprecated // For use by the Helidon Config subsystem only.
    @Override // PollableSource
    public boolean isModified(Stamp lastKnownStamp) {
        Stamp stamp = this.stamper.get();
        if (!stamp.eTags().equals(lastKnownStamp.eTags())) {
            return true;
        }
        return stamp.earliestExpiration().isBefore(lastKnownStamp.earliestExpiration());
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

    private static Stamp toStamp(Collection<SecretSummary> secretSummaries,
                                 Supplier<? extends Secrets> secretsSupplier) {
        if (secretSummaries.isEmpty()) {
            return new Stamp();
        }
        Set<String> eTags = ConcurrentHashMap.newKeySet();
        Collection<Callable<Void>> tasks = new ArrayList<>(secretSummaries.size());
        Secrets secrets = secretsSupplier.get();
        for (SecretSummary ss : secretSummaries) {
            if (ss.getLifecycleState() == Active) {
                tasks.add(() -> {
                    GetSecretBundleResponse response = secrets.getSecretBundle(secretBundleRequest(ss.getId()));
                    eTags.add(response.getEtag());
                    return null; // Callable<Void>; null is the only possible return value
                });
            }
        }
        completeTasks(tasks, secrets);
        return toStamp(secretSummaries, eTags);
    }

    private static void completeTasks(Collection<Callable<Void>> tasks, AutoCloseable autoCloseable) {
        try (ExecutorService es = newVirtualThreadPerTaskExecutor();
                autoCloseable) {
            completeTasks(es, tasks);
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

    private static void completeTasks(ExecutorService es, Collection<Callable<Void>> tasks) {
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

    private static <T> List<Future<T>> invokeAllUnchecked(ExecutorService es, Collection<Callable<T>> tasks) {
        try {
            return es.invokeAll(tasks);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    private static GetSecretBundleRequest secretBundleRequest(String secretId) {
        return GetSecretBundleRequest.builder()
                .secretId(secretId)
                .stage(GetSecretBundleRequest.Stage.Current) // extremely important
                .build();
    }

    // Suppress "[try] auto-closeable resource Vaults has a member method close() that could throw
    // InterruptedException" since we handle it.
    @SuppressWarnings("try")
    private static Collection<SecretSummary> secretSummaries(Supplier<? extends Vaults> vaultsSupplier,
                                                             ListSecretsRequest listSecretsRequest) {
        try (Vaults v = vaultsSupplier.get()) {
            return v.listSecrets(listSecretsRequest).getItems()
                    .stream()
                    .filter(ss -> EXTANT_STATES.contains(ss.getLifecycleState()))
                    .toList();
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

    private static Base64SecretBundleContentDetails
    secretBundleContentDetails(GetSecretBundleRequest request,
                               Function<GetSecretBundleRequest, GetSecretBundleResponse> f,
                               Consumer<String> eTags) {
        GetSecretBundleResponse response = f.apply(request);
        eTags.accept(response.getEtag());
        SecretBundle sb = response.getSecretBundle();
        return (Base64SecretBundleContentDetails) sb.getSecretBundleContent();
    }

    private static ValueNode valueNode(Function<GetSecretBundleRequest, Base64SecretBundleContentDetails> f,
                                       String secretId,
                                       Base64.Decoder base64Decoder) {
        return valueNode(f.apply(secretBundleRequest(secretId)), base64Decoder);
    }

    private static ValueNode valueNode(Base64SecretBundleContentDetails details, Base64.Decoder base64Decoder) {
        return valueNode(details.getContent(), base64Decoder);
    }

    private Optional<NodeContent> load(Supplier<? extends Vaults> vaultsSupplier,
                                       Supplier<? extends Secrets> secretsSupplier,
                                       ListSecretsRequest listSecretsRequest) {
        Collection<SecretSummary> secretSummaries = secretSummaries(vaultsSupplier, listSecretsRequest);
        return this.load(secretSummaries, secretsSupplier);
    }

    private Optional<NodeContent> load(Collection<SecretSummary> secretSummaries,
                                       Supplier<? extends Secrets> secretsSupplier) {
        if (secretSummaries.isEmpty()) {
            return Optional.empty();
        }
        ConcurrentMap<String, ValueNode> valueNodes = new ConcurrentHashMap<>();
        Set<String> eTags = ConcurrentHashMap.newKeySet();
        Collection<Callable<Void>> tasks = new ArrayList<>();
        Base64.Decoder decoder = Base64.getDecoder();
        Secrets secrets = secretsSupplier.get();
        for (SecretSummary ss : secretSummaries) {
            tasks.add(task(valueNodes::put,
                           eTags::add,
                           ss.getSecretName(),
                           secrets::getSecretBundle,
                           ss.getId(),
                           decoder));
        }
        completeTasks(tasks, secrets);
        ObjectNode.Builder objectNodeBuilder = ObjectNode.builder();
        for (Entry<String, ValueNode> e : valueNodes.entrySet()) {
            objectNodeBuilder.addValue(e.getKey(), e.getValue());
        }
        return Optional.of(NodeContent.builder()
                                   .node(objectNodeBuilder.build())
                                   .stamp(toStamp(secretSummaries, eTags))
                                   .build());
    }

    /**
     * An {@link AbstractConfigSourceBuilder} and {@link PollableSource.Builder} that {@linkplain #build() builds}
     * {@link SecretBundleNodeConfigSource} instances.
     */
    public static final class Builder extends AbstractSecretBundleConfigSource.Builder<Builder>
        implements PollableSource.Builder<Builder> {

        private String compartmentOcid;
        private Supplier<? extends Vaults> vaultsSupplier;

        private Builder() {
            super();
            VaultsClient.Builder vcb = VaultsClient.builder();
            this.vaultsSupplier = () -> vcb.build(adpSupplier().get());
        }

        /**
         * Creates and returns a new {@link SecretBundleNodeConfigSource} instance initialized from the state of this
         * {@link Builder}.
         *
         * @return a new {@link SecretBundleNodeConfigSource}
         */
        public SecretBundleNodeConfigSource build() {
            return new SecretBundleNodeConfigSource(this);
        }

        /**
         * Sets the (required) OCID of the OCI compartment housing the vault from which a {@link
         * SecretBundleNodeConfigSource} will retrieve values.
         *
         * @param compartmentOcid a valid OCID identifying an OCI compartment; must not be {@code null}
         * @return this {@link Builder}
         * @throws NullPointerException if {@code compartmentId} is {@code null}
         */
        public Builder compartmentOcid(String compartmentOcid) {
            this.compartmentOcid = Objects.requireNonNull(compartmentOcid, "compartmentOcid");
            return this;
        }

        /**
         * Configures this {@link Builder} from the supplied meta-configuration.
         *
         * @param metaConfig the meta-configuration; must not be {@code null}
         * @return this {@link Builder}
         * @throws NullPointerException if {@code metaConfig} is {@code null}
         */
        @Override // AbstractConfigSourceBuilder<Builder, Void>
        public Builder config(Config metaConfig) {
            metaConfig.get("compartment-ocid")
                    .asString()
                    .filter(Predicate.not(String::isBlank))
                    .ifPresentOrElse(this::compartmentOcid,
                                     () -> {
                                         if (LOGGER.isLoggable(WARNING)) {
                                             LOGGER.log(WARNING,
                                                        "No meta-configuration value supplied for "
                                                                + metaConfig.key()
                                                                .toString() + "." + COMPARTMENT_OCID_PROPERTY_NAME
                                                                + "); resulting ConfigSource will be empty");
                                         }
                                     });
            return super.config(metaConfig);
        }

        /**
         * Sets the {@link PollingStrategy} for use by this {@link Builder}.
         *
         * <p>If this method is never called, no {@link PollingStrategy} will be used by this {@link Builder}.</p>
         *
         * <p>The implementation of this method calls {@link
         * io.helidon.config.AbstractSourceBuilder#pollingStrategy(PollingStrategy)
         * super.pollingStrategy(pollingStrategy)} and returns the result.</p>
         *
         * @param pollingStrategy a {@link PollingStrategy}; must not be {@code null}
         * @return this {@link Builder}
         * @throws NullPointerException if {@code pollingStrategy} is {@code null}
         * @see PollableSource
         * @see PollingStrategy
         */
        @Override // PollableSource.Builder<Builder>
        public Builder pollingStrategy(PollingStrategy pollingStrategy) {
            return super.pollingStrategy(pollingStrategy);
        }

        /**
         * Uses the supplied {@link Supplier} of {@link Vaults} instances, instead of the default one, for
         * communicating with the OCI Vaults API.
         *
         * @param vaultsSupplier the non-default {@link Supplier} to use; must not be {@code null}
         * @return this {@link Builder}
         * @throws NullPointerException if {@code vaultsSupplier} is {@code null}
         */
        public Builder vaultsSupplier(Supplier<? extends Vaults> vaultsSupplier) {
            this.vaultsSupplier = Objects.requireNonNull(vaultsSupplier, "vaultsSupplier");
            return this;
        }

    }

    /**
     * A pairing of a {@link Set} of entity tags with an {@link Instant} identifying the earliest expiration
     * of a Secret indirectly identified by one of those tags.
     *
     * @param eTags              a {@link Set} of entity tags
     * @param earliestExpiration an {@link Instant} identifying the earliest expiration of a Secret indirectly
     *                           identified by one of the supplied tags
     */
    public record Stamp(Set<?> eTags, Instant earliestExpiration) {

        /**
         * Creates a new {@link Stamp}.
         */
        public Stamp() {
            this(Set.of(), now());
        }

        /**
         * Creates a new {@link Stamp}.
         *
         * @throws NullPointerException if any argument is {@code null}
         */
        public Stamp {
            eTags = Set.copyOf(Objects.requireNonNull(eTags, "eTags"));
            Objects.requireNonNull(earliestExpiration);
        }

    }

}
