/*
 * Copyright (c) 2021, 2025 Oracle and/or its affiliates.
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
package io.helidon.lra.coordinator;

import java.lang.System.Logger.Level;
import java.net.URI;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Stream;

import io.helidon.common.LazyValue;
import io.helidon.config.Config;
import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.scheduling.FixedRateInvocation;
import io.helidon.scheduling.Scheduling;
import io.helidon.scheduling.Task;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.HttpService;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonBuilderFactory;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import org.eclipse.microprofile.lra.annotation.LRAStatus;
import org.eclipse.microprofile.lra.annotation.ws.rs.LRA;

import static io.helidon.http.Status.CREATED_201;
import static io.helidon.http.Status.GONE_410;
import static io.helidon.http.Status.NOT_FOUND_404;
import static io.helidon.http.Status.OK_200;
import static io.helidon.http.Status.PRECONDITION_FAILED_412;

/**
 * LRA coordinator with Narayana like rest api.
 */
public class CoordinatorService implements HttpService {

    /**
     * Configuration prefix.
     */
    public static final String CONFIG_PREFIX = "helidon.lra.coordinator";
    static final String CLIENT_ID_PARAM_NAME = "ClientID";
    static final String TIME_LIMIT_PARAM_NAME = "TimeLimit";
    static final String PARENT_LRA_PARAM_NAME = "ParentLRA";
    static final String COORDINATOR_URL_KEY = "url";
    static final String DEFAULT_COORDINATOR_URL = "http://localhost:8070/lra-coordinator";

    private static final System.Logger LOGGER = System.getLogger(CoordinatorService.class.getName());
    private static final HeaderName LRA_HTTP_CONTEXT_HEADER = HeaderNames.create(LRA.LRA_HTTP_CONTEXT_HEADER);
    private static final HeaderName LRA_HTTP_RECOVERY_HEADER = HeaderNames.create(LRA.LRA_HTTP_RECOVERY_HEADER);

    private static final Set<LRAStatus> RECOVERABLE_STATUSES = Set.of(LRAStatus.Cancelling, LRAStatus.Closing, LRAStatus.Active);
    private static final JsonBuilderFactory JSON = Json.createBuilderFactory(Collections.emptyMap());
    private final AtomicReference<CompletableFuture<Void>> completedRecovery = new AtomicReference<>(new CompletableFuture<>());

    private final LraPersistentRegistry lraPersistentRegistry;

    private final LazyValue<URI> coordinatorURL;
    private final Config config;
    private Task recoveryTask;
    private Task persistTask = null;
    private volatile boolean shuttingDown = false;

    CoordinatorService(LraPersistentRegistry lraPersistentRegistry, Supplier<URI> coordinatorUriSupplier, Config config) {
        this.lraPersistentRegistry = lraPersistentRegistry;
        this.coordinatorURL = LazyValue.create(coordinatorUriSupplier);
        this.config = config;
        init();
    }

    /**
     * Create a new Lra coordinator.
     *
     * @return coordinator
     */
    public static CoordinatorService create() {
        return builder()
                .build();
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
     * Gracefully shutdown coordinator.
     */
    public void shutdown() {
        shuttingDown = true;
        Stream.of(recoveryTask, persistTask)
                .filter(Objects::nonNull)
                .forEach(Task::close);
        lraPersistentRegistry.save();
    }

    @Override
    public void routing(HttpRules rules) {
        rules
                .get("/", this::get)
                .get("/recovery", this::recovery)
                .get("/{LraId}/recovery", this::recovery)
                .post("/start", this::start)
                .put("/{LraId}/close", this::close)
                .put("/{LraId}/cancel", this::cancel)
                .put("/{LraId}", this::join)
                .get("/{LraId}", this::get)
                .get("/{LraId}/status", this::status)
                .put("/{LraId}/remove", this::leave);
    }

    /**
     * Get LRA by lraId.
     *
     * @param lraId without coordinator uri prefix
     * @return LRA when managed by this coordinator or null
     */
    public Lra lra(String lraId) {
        return this.lraPersistentRegistry.get(lraId);
    }

    LazyValue<URI> coordinatorURL() {
        return coordinatorURL;
    }

    private void init() {
        lraPersistentRegistry.load(this);
        recoveryTask = Scheduling.fixedRate()
                .delay(config.get("recovery-interval").asLong().orElse(200L))
                .initialDelay(200)
                .timeUnit(TimeUnit.MILLISECONDS)
                .task(this::tick)
                .build();

        if (config.get("periodical-persist").asBoolean().orElse(false)) {
            persistTask = Scheduling.fixedRate()
                    .delay(config.get("persist-interval").asLong().orElse(5000L))
                    .initialDelay(200)
                    .timeUnit(TimeUnit.MILLISECONDS)
                    .task(inv -> lraPersistentRegistry.save())
                    .build();
        }
    }

    /**
     * Ask coordinator to start new LRA and return its id.
     *
     * @param req HTTP Request
     * @param res HTTP Response
     */
    private void start(ServerRequest req, ServerResponse res) {

        long timeLimit = req.query().first(TIME_LIMIT_PARAM_NAME).map(Long::valueOf).orElse(0L);
        String parentLRA = req.query().first(PARENT_LRA_PARAM_NAME).orElse("");

        String lraUUID = UUID.randomUUID().toString();
        URI lraId = coordinatorUriWithPath(lraUUID);
        if (!parentLRA.isEmpty()) {
            LraImpl parent = lraPersistentRegistry.get(parentLRA.replace(coordinatorURL.get().toASCIIString() + "/", ""));
            if (parent != null) {
                LraImpl childLra = new LraImpl(this, lraUUID, URI.create(parentLRA), this.config);
                childLra.setupTimeout(timeLimit);
                lraPersistentRegistry.put(lraUUID, childLra);
                parent.addChild(childLra);
            }
        } else {
            LraImpl newLra = new LraImpl(this, lraUUID, config);
            newLra.setupTimeout(timeLimit);
            lraPersistentRegistry.put(lraUUID, newLra);
        }

        res.headers().add(LRA_HTTP_CONTEXT_HEADER, lraId.toASCIIString());
        res.status(CREATED_201)
                .send(lraId.toString());
    }

    /**
     * Close LRA if its active. Should cause coordinator to complete its participants.
     *
     * @param req HTTP Request
     * @param res HTTP Response
     */
    private void close(ServerRequest req, ServerResponse res) {
        String lraId = req.path().pathParameters().get("LraId");
        LraImpl lra = lraPersistentRegistry.get(lraId);
        if (lra == null) {
            res.status(NOT_FOUND_404).send();
            return;
        }
        if (lra.lraStatus().get() != LRAStatus.Active) {
            // Already time-outed
            res.status(GONE_410).send();
            return;
        }
        lra.close();
        res.status(OK_200).send();
    }

    /**
     * Cancel LRA if its active. Should cause coordinator to compensate its participants.
     *
     * @param req HTTP Request
     * @param res HTTP Response
     */
    private void cancel(ServerRequest req, ServerResponse res) {
        String lraId = req.path().pathParameters().get("LraId");
        LraImpl lra = lraPersistentRegistry.get(lraId);
        if (lra == null) {
            res.status(NOT_FOUND_404).send();
            return;
        }
        lra.cancel();
        res.status(OK_200).send();
    }

    /**
     * Join existing LRA with participant.
     *
     * @param req HTTP Request
     * @param res HTTP Response
     */
    private void join(ServerRequest req, ServerResponse res) {

        String lraId = req.path().pathParameters().get("LraId");
        String compensatorLink = req.headers().first(HeaderNames.LINK).orElse("");

        LraImpl lra = lraPersistentRegistry.get(lraId);
        if (lra == null) {
            res.status(NOT_FOUND_404).send();
            return;
        } else if (lra.checkTimeout()) {
            // too late to join
            res.status(PRECONDITION_FAILED_412).send();
            return;
        }
        lra.addParticipant(compensatorLink);
        String recoveryUrl = coordinatorUriWithPath("/" + lraId + "/recovery").toASCIIString();

        res.headers().set(LRA_HTTP_RECOVERY_HEADER, recoveryUrl);
        res.headers().set(HeaderNames.LOCATION, recoveryUrl);
        res.status(OK_200)
                .send(recoveryUrl);
    }

    /**
     * Return status of specified LRA.
     *
     * @param req HTTP Request
     * @param res HTTP Response
     */
    private void status(ServerRequest req, ServerResponse res) {
        String lraId = req.path().pathParameters().get("LraId");
        LraImpl lra = lraPersistentRegistry.get(lraId);
        if (lra == null) {
            res.status(NOT_FOUND_404).send();
            return;
        }

        res.status(OK_200)
                .send(lra.lraStatus().get().name());
    }

    /**
     * Leave LRA. Supplied participant won't be part of specified LRA any more,
     * no compensation or completion will be executed on it.
     *
     * @param req HTTP Request
     * @param res HTTP Response
     */
    private void leave(ServerRequest req, ServerResponse res) {
        String lraId = req.path().pathParameters().get("LraId");
        String compensatorLinks = req.content().as(String.class);

        LraImpl lra = lraPersistentRegistry.get(lraId);
        if (lra == null) {
            res.status(NOT_FOUND_404).send();
        } else {
            lra.removeParticipant(compensatorLinks);
            res.status(OK_200).send();
        }
    }

    /**
     * Blocks until next recovery cycle is finished.
     *
     * @param req HTTP Request
     * @param res HTTP Response
     */
    private void recovery(ServerRequest req, ServerResponse res) {
        nextRecoveryCycle();

        Optional<String> lraUUID = req.query().first("lraId")
                .or(() -> req.path().pathParameters().first("LraId").asOptional())
                .map(l -> {
                    if (l.lastIndexOf("/") != -1 && l.lastIndexOf("/") + 1 < l.length()) {
                        return l.substring(l.lastIndexOf("/") + 1);
                    } else {
                        return l;
                    }
                });

        if (lraUUID.isPresent()) {
            LraImpl lra = lraPersistentRegistry.get(lraUUID.get());
            if (lra != null) {
                if (RECOVERABLE_STATUSES.contains(lra.lraStatus().get())) {
                    JsonObject json = JSON.createObjectBuilder()
                            .add("lraId", lra.lraId())
                            .add("status", lra.lraStatus().get().name())
                            .add("recovering", Set.of(LRAStatus.Closed, LRAStatus.Cancelled).contains(lra.lraStatus().get()))
                            .build();
                    res.status(OK_200).send(json);
                } else {
                    res.status(OK_200).send(JsonValue.EMPTY_JSON_OBJECT);
                }
            } else {
                res.status(NOT_FOUND_404).send(JsonValue.EMPTY_JSON_OBJECT);
            }
        } else {
            JsonArray jsonValues = lraPersistentRegistry
                    .stream()
                    .filter(lra -> RECOVERABLE_STATUSES.contains(lra.lraStatus().get()))
                    .map(l -> JSON.createObjectBuilder()
                            .add("lraId", l.lraId())
                            .add("status", l.lraStatus().get().name())
                            .build()
                    )
                    .collect(JSON::createArrayBuilder, JsonArrayBuilder::add, JsonArrayBuilder::addAll)
                    .build();

            res.status(OK_200).send(jsonValues);
        }
    }

    private void get(ServerRequest req, ServerResponse res) {
        Optional<String> lraId = req.path().pathParameters().first("LraId")
                .or(() -> req.query().first("lraId").asOptional());

        JsonArray array = lraPersistentRegistry
                .stream()
                // filter by lraId param or dont filter at all
                .filter(lra -> lraId.map(id -> lra.lraId().equals(id)).orElse(true))
                .map(l -> JSON.createObjectBuilder()
                        .add("lraId", l.lraId())
                        .add("status", l.lraStatus().get().name())
                        .build()
                )
                .collect(JSON::createArrayBuilder, JsonArrayBuilder::add, JsonArrayBuilder::addAll)
                .build();

        res.status(OK_200)
                .send(array);
    }

    private void tick(FixedRateInvocation inv) {
        if (shuttingDown) {
            return;
        }
        lraPersistentRegistry.stream().forEach(lra -> {
            if (shuttingDown) {
                return;
            }
            if (lra.isReadyToDelete()) {
                lraPersistentRegistry.remove(lra.lraId());
            } else {
                if (LRAStatus.Cancelling == lra.lraStatus().get()) {
                    LOGGER.log(Level.DEBUG, "Recovering {0}", lra.lraId());
                    lra.cancel();
                }
                if (LRAStatus.Closing == lra.lraStatus().get()) {
                    LOGGER.log(Level.DEBUG, "Recovering {0}", lra.lraId());
                    lra.close();
                }
                if (lra.checkTimeout() && lra.lraStatus().get().equals(LRAStatus.Active)) {
                    LOGGER.log(Level.DEBUG, "Timeouting {0} ", lra.lraId());
                    lra.triggerTimeout();
                }
                if (Set.of(LRAStatus.Closed, LRAStatus.Cancelled).contains(lra.lraStatus().get())) {
                    // If a participant is unable to complete or compensate immediately or because of a failure
                    // then it must remember the fact (by reporting its' status via the @Status method)
                    // until explicitly told that it can clean up using this @Forget annotation.
                    LOGGER.log(Level.DEBUG, "Forgetting {0} {1}", new Object[] {lra.lraStatus().get(), lra.lraId()});
                    lra.tryForget();
                    lra.tryAfter();
                }
            }
        });
        completedRecovery.getAndSet(new CompletableFuture<>()).complete(null);
    }

    private void nextRecoveryCycle() {
        try {
            completedRecovery.get().get(1, TimeUnit.SECONDS);
            //wait for the second one, as first could have been in progress
            completedRecovery.get().get(1, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            LOGGER.log(Level.TRACE, "Failed to get recovery cycle result, ignoring", e);
        }
    }

    private URI coordinatorUriWithPath(String additionalPath) {
        return URI.create(coordinatorURL.get().toASCIIString() + "/" + additionalPath);
    }

    /**
     * Coordinator builder.
     */
    public static final class Builder implements io.helidon.common.Builder<Builder, CoordinatorService> {

        private Config config;
        private LraPersistentRegistry lraPersistentRegistry;
        private Supplier<URI> uriSupplier = () -> URI.create(config.get(COORDINATOR_URL_KEY)
                                                                     .asString()
                                                                     .orElse(DEFAULT_COORDINATOR_URL));

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
         * Supplier for coordinator url.
         * For supplying url after we know the port of the started server.
         *
         * @param uriSupplier coordinator url
         * @return this builder
         */
        public Builder url(Supplier<URI> uriSupplier) {
            this.uriSupplier = uriSupplier;
            return this;
        }

        @Override
        public CoordinatorService build() {
            if (config == null) {
                config = Config.empty();
            }
            if (lraPersistentRegistry == null) {
                lraPersistentRegistry = new LraDatabasePersistentRegistry(config);
            }
            return new CoordinatorService(lraPersistentRegistry, uriSupplier, config);
        }

        /**
         * Custom persistent registry for saving and loading the state of the coordinator.
         * Coordinator is not persistent by default.
         *
         * @param lraPersistentRegistry custom persistent registry
         * @return this builder
         */
        Builder persistentRegistry(LraPersistentRegistry lraPersistentRegistry) {
            this.lraPersistentRegistry = lraPersistentRegistry;
            return this;
        }
    }
}
