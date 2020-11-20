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
package io.helidon.microprofile.lra.coordinator;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.BeforeDestroyed;
import javax.enterprise.context.Initialized;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

import io.helidon.common.reactive.Single;
import io.helidon.microprofile.scheduling.FixedRate;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.lra.annotation.LRAStatus;

import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.LRA_HTTP_CONTEXT_HEADER;
import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.LRA_HTTP_RECOVERY_HEADER;

/**
 * Mock LRA coordinator with Narayana like rest api for testing .
 */
@ApplicationScoped
@Path("lra-coordinator")
public class Coordinator {

    static final String CLIENT_ID_PARAM_NAME = "ClientID";
    static final String TIME_LIMIT_PARAM_NAME = "TimeLimit";
    static final String PARENT_LRA_PARAM_NAME = "ParentLRA";

    private static final Logger LOGGER = Logger.getLogger(Coordinator.class.getName());
    private static final Set<LRAStatus> RECOVERABLE_STATUSES = Set.of(LRAStatus.Cancelling, LRAStatus.Closing, LRAStatus.Active);

    private final LraPersistentRegistry lraPersistentRegistry = new LraPersistentRegistry();

    private final AtomicReference<CompletableFuture<Void>> completedRecovery = new AtomicReference<>(new CompletableFuture<>());

    @Inject
    @ConfigProperty(name = "lra.tck.coordinator.persist", defaultValue = "false")
    private Boolean persistent;

    @Inject
    @ConfigProperty(name = "mp.lra.coordinator.url", defaultValue = "http://localhost:8070/lra-coordinator")
    private String coordinatorURL;

    private void init(@Observes @Initialized(ApplicationScoped.class) Object init) {
        if (persistent) {
            lraPersistentRegistry.load();
        }
    }

    private void whenApplicationTerminates(@Observes @BeforeDestroyed(ApplicationScoped.class) final Object event) {
        if (persistent) {
            lraPersistentRegistry.save();
        }
    }

    /**
     * Ask coordinator to start new LRA and return its id.
     *
     * @param parentLRA in case new LRA should be a child of already existing one
     * @param clientId  id specifying originating method/resource
     * @param timeLimit after what time should be LRA cancelled automatically
     * @return id of the new LRA as
     */
    @POST
    @Path("start")
    @Produces(MediaType.TEXT_PLAIN)
    public Response start(
            @QueryParam(CLIENT_ID_PARAM_NAME) @DefaultValue("") String clientId,
            @QueryParam(TIME_LIMIT_PARAM_NAME) @DefaultValue("0") Long timeLimit,
            @QueryParam(PARENT_LRA_PARAM_NAME) @DefaultValue("") String parentLRA) throws WebApplicationException {

        String lraUUID = UUID.randomUUID().toString();
        URI lraId = UriBuilder.fromPath(coordinatorURL).path(lraUUID).build();
        if (!parentLRA.isEmpty()) {
            Lra parent = lraPersistentRegistry.get(parentLRA.replace(coordinatorURL, ""));
            if (parent != null) {
                Lra childLra = new Lra(lraUUID, UriBuilder.fromPath(parentLRA).build());
                childLra.setupTimeout(timeLimit);
                lraPersistentRegistry.put(lraUUID, childLra);
                parent.addChild(childLra);
            }
        } else {
            Lra newLra = new Lra(lraUUID);
            newLra.setupTimeout(timeLimit);
            lraPersistentRegistry.put(lraUUID, newLra);
        }
        return Response.created(lraId)
                .entity(lraId.toString())
                .header(LRA_HTTP_CONTEXT_HEADER, lraId)
                .build();
    }

    /**
     * Close LRA if its active. Should cause coordinator to complete its participants.
     *
     * @param lraId id of the LRA to be closed
     * @return 200, 404 or 410
     * @throws NotFoundException
     */
    @PUT
    @Path("{LraId}/close")
    @Produces(MediaType.TEXT_PLAIN)
    public Response close(
            @PathParam("LraId") String lraId) throws NotFoundException {
        Lra lra = lraPersistentRegistry.get(lraId);
        if (lra == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        if (lra.status().get() != LRAStatus.Active) {
            // Already time-outed
            return Response.status(Response.Status.GONE).build();
        }
        lra.close();
        return Response.ok().build();
    }

    /**
     * Cancel LRA if its active. Should cause coordinator to compensate its participants.
     *
     * @param lraId id of the LRA to be cancelled
     * @return 200 or 404
     * @throws NotFoundException
     */
    @PUT
    @Path("{LraId}/cancel")
    public Response cancel(
            @PathParam("LraId") String lraId) throws NotFoundException {
        Lra lra = lraPersistentRegistry.get(lraId);
        if (lra == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        lra.cancel();
        return Response.ok().build();
    }

    /**
     * Join existing LRA with participant.
     *
     * @param lraId           id of existing LRA
     * @param timeLimit       time limit in milliseconds after which should be LRA cancelled, 0 means never
     * @param compensatorLink participant metadata with compensate URLs delimited by commas
     * @return recovery URI if supported by coordinator or empty
     * @throws NotFoundException
     */
    @PUT
    @Path("{LraId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response join(
            @PathParam("LraId") String lraId,
            @QueryParam(TIME_LIMIT_PARAM_NAME) @DefaultValue("0") long timeLimit,
            @HeaderParam("Link") @DefaultValue("") String compensatorLink) throws NotFoundException {
        Lra lra = lraPersistentRegistry.get(lraId);
        if (lra == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        } else if (lra.checkTimeout()) {
            // too late to join
            return Response.status(Response.Status.PRECONDITION_FAILED).build();
        }
        lra.addParticipant(compensatorLink);
        String recoveryUrl = coordinatorURL + lraId;
        try {
            return Response.ok()
                    .entity(recoveryUrl)
                    .location(new URI(recoveryUrl))
                    .header(LRA_HTTP_RECOVERY_HEADER, recoveryUrl)
                    .build();
        } catch (URISyntaxException e) {
            LOGGER.log(Level.SEVERE, "Error when joining LRA " + lraId, e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Return status of specified LRA.
     *
     * @param lraId id of the queried LRA
     * @return {@link org.eclipse.microprofile.lra.annotation.LRAStatus} of the queried LRA
     */
    @GET
    @Path("{LraId}/status")
    @Produces(MediaType.TEXT_PLAIN)
    public Response status(@PathParam("LraId") String lraId) {
        Lra lra = lraPersistentRegistry.get(lraId);
        if (lra == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .build();
        }

        return Response.ok()
                .entity(lra.status().get().name())
                .build();
    }

    /**
     * Leave LRA. Supplied participant won't be part of specified LRA any more,
     * no compensation or completion will be executed on it.
     *
     * @param lraId          id of the LRA that should be left by supplied participant
     * @param compensatorUrl participant metadata with compensate URLs delimited by commas
     * @return 200 or 404
     */
    @PUT
    @Path("{LraId}/remove")
    @Produces(MediaType.APPLICATION_JSON)
    public Response leave(@PathParam("LraId") String lraId, String compensatorUrl) {
        Lra lra = lraPersistentRegistry.get(lraId);
        if (lra == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        lra.removeParticipant(compensatorUrl);
        return Response.ok().build();
    }

    /**
     * Blocks until next recovery cycle is finished.
     * @return 200
     */
    @GET
    @Path("recovery")
    @Produces(MediaType.TEXT_PLAIN)
    public Response recovery() {
        return nextRecoveryCycle()
                .map(String::valueOf)
                .onCompleteResume(lraPersistentRegistry
                        .stream()
                        .filter(lra -> RECOVERABLE_STATUSES.contains(lra.status().get()))
                        .map(lra -> lra.status().get().name() + "-" + lra.lraId())
                        .collect(Collectors.joining(","))
                ).map(s -> Response.ok(s).build())
                .first()
                .await();
    }

    @FixedRate(value = 500, timeUnit = TimeUnit.MILLISECONDS)
    void tick() {
        lraPersistentRegistry.stream().forEach(lra -> {
            if (lra.isReadyToDelete()) {
                lraPersistentRegistry.remove(lra.lraId());
            } else {
                synchronized (this) {
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
                        lra.trySendAfterLRA();
                    }
                }
            }
        });
        completedRecovery.getAndSet(new CompletableFuture<>()).complete(null);
    }

    private Single<Void> nextRecoveryCycle() {
        return Single.create(completedRecovery.get(), true)
                //wait for the second one, as first could have been in progress
                .onCompleteResumeWith(Single.create(completedRecovery.get(), true))
                .ignoreElements();
    }
}
