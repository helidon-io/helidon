/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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
 *
 */
package io.helidon.lra.coordinator;

import java.net.URI;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.common.reactive.Single;
import io.helidon.config.Config;
import io.helidon.scheduling.FixedRateInvocation;
import io.helidon.scheduling.Scheduling;
import io.helidon.scheduling.Task;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import io.helidon.webserver.Service;

import org.eclipse.microprofile.lra.annotation.LRAStatus;

import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.LRA_HTTP_CONTEXT_HEADER;
import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.LRA_HTTP_RECOVERY_HEADER;

/**
 * LRA coordinator with Narayana like rest api.
 */
public class CoordinatorService implements Service {

    /**
     * Configuration prefix.
     */
    public static final String CONFIG_PREFIX = "helidon.lra.coordinator";
    static final String CLIENT_ID_PARAM_NAME = "ClientID";
    static final String TIME_LIMIT_PARAM_NAME = "TimeLimit";
    static final String PARENT_LRA_PARAM_NAME = "ParentLRA";

    private static final Logger LOGGER = Logger.getLogger(CoordinatorService.class.getName());
    private static final Set<LRAStatus> RECOVERABLE_STATUSES = Set.of(LRAStatus.Cancelling, LRAStatus.Closing, LRAStatus.Active);

    private final AtomicReference<CompletableFuture<Void>> completedRecovery = new AtomicReference<>(new CompletableFuture<>());

    private final LraPersistentRegistry lraPersistentRegistry;

    private final String coordinatorURL;
    private final Config config;
    private Task scheduled;

    CoordinatorService(LraPersistentRegistry lraPersistentRegistry, Config config) {
        this.lraPersistentRegistry = lraPersistentRegistry;
        coordinatorURL = config.get("url").asString().orElse("http://localhost:8070/lra-coordinator");
        this.config = config;
        init();
    }

    private void init() {
        lraPersistentRegistry.load();
        scheduled = Scheduling.fixedRateBuilder()
                .delay(300)
                .initialDelay(200)
                .timeUnit(TimeUnit.MILLISECONDS)
                .task(this::tick)
                .build();
    }

    /**
     * Gracefully shutdown coordinator.
     */
    public void shutdown() {
        scheduled.executor().shutdown();
        try {
            if (!scheduled.executor().awaitTermination(5, TimeUnit.SECONDS)) {
                LOGGER.log(Level.WARNING, "Shutdown of the scheduled task took too long.");
            }
        } catch (InterruptedException e) {
            LOGGER.log(Level.WARNING, "Shutdown of the scheduled task was interrupted.", e);
        }
        lraPersistentRegistry.save();
    }

    @Override
    public void update(Routing.Rules rules) {
        rules
                .get("/", (req, res) -> {
                    res.send("Helidon coordinator");
                })
                .post("/start", this::start)
                .put("/{LraId}/close", this::close)
                .put("/{LraId}/cancel", this::cancel)
                .put("/{LraId}", this::join)
                .get("/{LraId}/status", this::status)
                .put("/{LraId}/remove", this::leave)
                .get("/recovery", this::recovery);
    }

    /**
     * Ask coordinator to start new LRA and return its id.
     *
     * @param req HTTP Request
     * @param res HTTP Response
     */
    private void start(ServerRequest req, ServerResponse res) {

        long timeLimit = req.queryParams().first(TIME_LIMIT_PARAM_NAME).map(Long::valueOf).orElse(0L);
        String parentLRA = req.queryParams().first(PARENT_LRA_PARAM_NAME).orElse("");

        String lraUUID = UUID.randomUUID().toString();
        URI lraId = URI.create(coordinatorURL + "/" + lraUUID);
        if (!parentLRA.isEmpty()) {
            Lra parent = lraPersistentRegistry.get(parentLRA.replace(coordinatorURL, ""));
            if (parent != null) {
                Lra childLra = new Lra(lraUUID, URI.create(parentLRA), this.config);
                childLra.setupTimeout(timeLimit);
                lraPersistentRegistry.put(lraUUID, childLra);
                parent.addChild(childLra);
            }
        } else {
            Lra newLra = new Lra(lraUUID, config);
            newLra.setupTimeout(timeLimit);
            lraPersistentRegistry.put(lraUUID, newLra);
        }

        res.headers().add(LRA_HTTP_CONTEXT_HEADER, lraId.toASCIIString());
        res.status(201)
                .send(lraId.toString());
    }

    /**
     * Close LRA if its active. Should cause coordinator to complete its participants.
     *
     * @param req HTTP Request
     * @param res HTTP Response
     */
    private void close(ServerRequest req, ServerResponse res) {
        String lraId = req.path().param("LraId");
        Lra lra = lraPersistentRegistry.get(lraId);
        if (lra == null) {
            res.status(404).send();
            return;
        }
        if (lra.status().get() != LRAStatus.Active) {
            // Already time-outed
            res.status(410).send();
            return;
        }
        lra.close();
        res.status(200).send();
    }

    /**
     * Cancel LRA if its active. Should cause coordinator to compensate its participants.
     *
     * @param req HTTP Request
     * @param res HTTP Response
     */
    private void cancel(ServerRequest req, ServerResponse res) {
        String lraId = req.path().param("LraId");
        Lra lra = lraPersistentRegistry.get(lraId);
        if (lra == null) {
            res.status(404).send();
            return;
        }
        lra.cancel();
        res.status(200).send();
    }

    /**
     * Join existing LRA with participant.
     *
     * @param req HTTP Request
     * @param res HTTP Response
     */
    private void join(ServerRequest req, ServerResponse res) {

        String lraId = req.path().param("LraId");
        String compensatorLink = req.headers().first("Link").orElse("");

        Lra lra = lraPersistentRegistry.get(lraId);
        if (lra == null) {
            res.status(404).send();
            return;
        } else if (lra.checkTimeout()) {
            // too late to join
            res.status(412).send();
            return;
        }
        lra.addParticipant(compensatorLink);
        String recoveryUrl = coordinatorURL + lraId;

        res.headers().put(LRA_HTTP_RECOVERY_HEADER, recoveryUrl);
        res.headers().put("Location", recoveryUrl);
        res.status(200)
                .send(recoveryUrl);
    }

    /**
     * Return status of specified LRA.
     *
     * @param req HTTP Request
     * @param res HTTP Response
     */
    private void status(ServerRequest req, ServerResponse res) {
        String lraId = req.path().param("LraId");
        Lra lra = lraPersistentRegistry.get(lraId);
        if (lra == null) {
            res.status(404).send();
            return;
        }

        res.status(200)
                .send(lra.status().get().name());
    }

    /**
     * Leave LRA. Supplied participant won't be part of specified LRA any more,
     * no compensation or completion will be executed on it.
     *
     * @param req HTTP Request
     * @param res HTTP Response
     */
    private void leave(ServerRequest req, ServerResponse res) {
        String lraId = req.path().param("LraId");
        req.content()
                .as(String.class)
                .forSingle(compensatorLinks -> {
                    Lra lra = lraPersistentRegistry.get(lraId);
                    if (lra == null) {
                        res.status(404).send();
                    } else {
                        lra.removeParticipant(compensatorLinks);
                        res.status(200).send();
                    }
                }).exceptionally(res::send);
    }

    /**
     * Blocks until next recovery cycle is finished.
     *
     * @param req HTTP Request
     * @param res HTTP Response
     */
    private void recovery(ServerRequest req, ServerResponse res) {
        Optional<String> lraId = req.queryParams().first("lraId");

        if (lraId.isPresent()) {
            Lra lra = lraPersistentRegistry.get(lraId.get());
            if (lra != null) {
                nextRecoveryCycle()
                        .map(String::valueOf)
                        .onCompleteResumeWith(Single.just(lra.status().get().name() + "-" + lra.lraId()))
                        .first()
                        .onError(res::send)
                        .forSingle(s -> res.status(200).send(s));
            } else {
                nextRecoveryCycle()
                        .map(String::valueOf)
                        .onError(res::send)
                        .forSingle(s -> res.status(404).send());
            }
        } else {
            nextRecoveryCycle()
                    .map(String::valueOf)
                    .onCompleteResumeWith(lraPersistentRegistry
                            .stream()
                            .filter(lra -> RECOVERABLE_STATUSES.contains(lra.status().get()))
                            .map(lra -> lra.status().get().name() + "-" + lra.lraId())
                            .flatMapIterable(s -> Set.of(s, ","))
                            .collect(StringBuilder::new, StringBuilder::append)
                            .map(StringBuilder::toString))
                    .first()
                    .onError(res::send)
                    .forSingle(s -> res.status(200).send(s));
        }
    }

    private void tick(FixedRateInvocation inv) {
        lraPersistentRegistry.stream().forEach(lra -> {
            if (lra.isReadyToDelete()) {
                lraPersistentRegistry.remove(lra.lraId());
            } else {
                if (LRAStatus.Cancelling == lra.status().get()) {
                    LOGGER.log(Level.FINE, "Recovering {0}", lra.lraId());
                    lra.cancel();
                }
                if (LRAStatus.Closing == lra.status().get()) {
                    LOGGER.log(Level.FINE, "Recovering {0}", lra.lraId());
                    lra.close();
                }
                if (lra.checkTimeout() && lra.status().get().equals(LRAStatus.Active)) {
                    LOGGER.log(Level.FINE, "Timeouting {0} ", lra.lraId());
                    lra.timeout();
                }
                if (Set.of(LRAStatus.Closed, LRAStatus.Cancelled).contains(lra.status().get())) {
                    // If a participant is unable to complete or compensate immediately or because of a failure
                    // then it must remember the fact (by reporting its' status via the @Status method)
                    // until explicitly told that it can clean up using this @Forget annotation.
                    LOGGER.log(Level.FINE, "Forgetting {0} {1}", new Object[] {lra.status().get(), lra.lraId()});
                    lra.tryForget();
                    lra.tryAfter();
                }
            }
        });
        lraPersistentRegistry.save();
        completedRecovery.getAndSet(new CompletableFuture<>()).complete(null);
    }

    private Single<Void> nextRecoveryCycle() {
        return Single.create(completedRecovery.get(), true)
                //wait for the second one, as first could have been in progress
                .onCompleteResumeWith(Single.create(completedRecovery.get(), true))
                .ignoreElements();
    }

    /**
     * Create a new Lra coordinator.
     *
     * @return coordinator
     */
    public static CoordinatorService create() {
        return builder().build();
    }

    /**
     * Create a new fluent API builder.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Coordinator builder.
     */
    public static final class Builder implements io.helidon.common.Builder<CoordinatorService> {

        private Config config;
        private LraPersistentRegistry lraPersistentRegistry;

        /**
         * Configuration needed for configuring coordinator.
         *
         * @param config config for Lra coordinator.
         * @return this builder
         */
        public Builder config(Config config) {
            this.config = config;
            return this;
        }

        /**
         * Custom persistent registry for saving and loading the state of the coordinator.
         * Coordinator is not persistent by default.
         *
         * @param lraPersistentRegistry custom persistent registry
         * @return this builder
         */
        public Builder persistentRegistry(LraPersistentRegistry lraPersistentRegistry) {
            this.lraPersistentRegistry = lraPersistentRegistry;
            return this;
        }

        @Override
        public CoordinatorService build() {
            if (config == null) {
                config = Config.create().get(CoordinatorService.CONFIG_PREFIX);
            }
            if (lraPersistentRegistry == null) {
                lraPersistentRegistry = new LraDatabasePersistentRegistry(config);
            }
            return new CoordinatorService(lraPersistentRegistry, config);
        }
    }
}
