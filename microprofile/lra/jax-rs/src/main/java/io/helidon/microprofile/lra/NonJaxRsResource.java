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
 */
package io.helidon.microprofile.lra;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.BeforeDestroyed;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import javax.ws.rs.core.Response;

import io.helidon.common.Reflected;
import io.helidon.common.configurable.ThreadPoolSupplier;
import io.helidon.common.http.HttpRequest;
import io.helidon.common.reactive.Single;
import io.helidon.config.Config;
import io.helidon.microprofile.server.RoutingPath;
import io.helidon.webserver.RequestHeaders;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import io.helidon.webserver.Service;

import org.eclipse.microprofile.lra.LRAResponse;
import org.eclipse.microprofile.lra.annotation.LRAStatus;
import org.eclipse.microprofile.lra.annotation.ParticipantStatus;

import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.LRA_HTTP_CONTEXT_HEADER;
import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.LRA_HTTP_ENDED_CONTEXT_HEADER;
import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.LRA_HTTP_PARENT_CONTEXT_HEADER;

@Reflected
@RoutingPath(NonJaxRsResource.CONTEXT_PATH)
class NonJaxRsResource implements Service {

    private static final Logger LOGGER = Logger.getLogger(NonJaxRsResource.class.getName());

    static final String CONTEXT_PATH = "/lra-participant";
    private static final String LRA_PARTICIPANT = "lra-participant";

    private final ExecutorService exec;

    private static final Map<ParticipantStatus, Supplier<Response>> PARTICIPANT_RESPONSE_BUILDERS =
            Map.of(
                    ParticipantStatus.Compensating, () -> LRAResponse.compensating(ParticipantStatus.Compensating),
                    ParticipantStatus.Compensated, () -> LRAResponse.compensated(ParticipantStatus.Compensated),
                    ParticipantStatus.Active, () -> Response.ok(ParticipantStatus.Active.name()).build(),
                    ParticipantStatus.FailedToCompensate,
                    () -> LRAResponse.failedToCompensate(ParticipantStatus.FailedToCompensate),
                    ParticipantStatus.Completing, () -> LRAResponse.compensating(ParticipantStatus.Completing),
                    ParticipantStatus.Completed, () -> LRAResponse.compensating(ParticipantStatus.Completed),
                    ParticipantStatus.FailedToComplete, () -> LRAResponse.failedToComplete(ParticipantStatus.FailedToComplete)
            );

    private final ParticipantService participantService;

    @Inject
    NonJaxRsResource(ParticipantService participantService, Config config) {
        this.participantService = participantService;
        exec = ThreadPoolSupplier.builder()
                .name(LRA_PARTICIPANT)
                .config(config.get("lra.participant.non-jax-rs.pool"))
                .build()
                .get();
    }

    @Override
    public void update(Routing.Rules rules) {
        rules
                .any("/{type}/{fqdn}/{methodName}", (req, res) -> {
                    LOGGER.log(Level.FINE, () -> "Non JAX-RS LRA resource " + req.method().name() + " " + req.absoluteUri());
                    RequestHeaders headers = req.headers();
                    HttpRequest.Path path = req.path();

                    URI lraId = headers.first(LRA_HTTP_CONTEXT_HEADER)
                            .or(() -> headers.first(LRA_HTTP_ENDED_CONTEXT_HEADER))
                            .map(URI::create)
                            .orElse(null);

                    URI parentId = headers.first(LRA_HTTP_PARENT_CONTEXT_HEADER)
                            .map(URI::create)
                            .orElse(null);

                    String fqdn = path.param("fqdn");
                    String method = path.param("methodName");
                    String type = path.param("type");

                    switch (type) {
                        case "compensate":
                        case "complete":
                            Single.defer(() -> participantService.invoke(fqdn, method, lraId, parentId))
                                    .observeOn(exec)
                                    .forSingle(result -> result.ifPresentOrElse(
                                            r -> sendResult(res, r),
                                            res::send
                                            )
                                    ).exceptionallyAccept(t -> sendError(lraId, req, res, t));
                            break;
                        case "afterlra":
                            req.content()
                                    .as(String.class)
                                    .map(LRAStatus::valueOf)
                                    .flatMapSingle(s -> Single.defer(() -> participantService.invoke(fqdn, method, lraId, s)))
                                    .observeOn(exec)
                                    .onComplete(res::send)
                                    .onError(t -> sendError(lraId, req, res, t))
                                    .ignoreElement();
                            break;
                        case "status":
                            Single.defer(() -> participantService.invoke(fqdn, method, lraId, null))
                                    .observeOn(exec)
                                    .forSingle(result -> result.ifPresentOrElse(
                                            r -> sendResult(res, r),
                                            // If the participant has already responded successfully
                                            // to a @Compensate or @Complete method invocation
                                            // then it MAY report 410 Gone HTTP status code
                                            // or in the case of non-JAX-RS method returning ParticipantStatus null.
                                            () -> res.status(Response.Status.GONE.getStatusCode()).send()))
                                    .exceptionallyAccept(t -> sendError(lraId, req, res, t));
                            break;
                        case "forget":
                            Single.defer(() -> participantService.invoke(fqdn, method, lraId, parentId))
                                    .observeOn(exec)
                                    .onComplete(res::send)
                                    .onError(t -> sendError(lraId, req, res, t))
                                    .ignoreElement();
                            break;
                        default:
                            LOGGER.severe(() -> "Unexpected non Jax-Rs LRA compensation type "
                                    + type + ": " + req.absoluteUri());
                            res.status(404).send();
                            break;
                    }
                });
    }

    private void sendError(URI lraId, ServerRequest req, ServerResponse res, Throwable t) {
        LOGGER.log(Level.FINE, t, () -> "Non Jax-Rs LRA participant resource "
                + req.absoluteUri()
                + " responds with error."
                + "LRA id: " + lraId);
        res.send(t);
    }

    private void sendResult(ServerResponse res, Object result) {
        if (result instanceof Response) {
            sendResponse(res, (Response) result);
        } else if (result instanceof ParticipantStatus) {
            ParticipantStatus status = (ParticipantStatus) result;
            sendResponse(res, PARTICIPANT_RESPONSE_BUILDERS
                    .getOrDefault(status, () -> Response.ok(status.name()).build())
                    .get());
        } else {
            res.send(result);
        }
    }

    private void sendResponse(ServerResponse res, Response response) {
        res.status(response.getStatus());
        response.getHeaders()
                .forEach((k, values) -> res.addHeader(k,
                        values.stream()
                                .map(String::valueOf)
                                .collect(Collectors.toList())
                ));
        Object entity = response.getEntity();
        if (entity == null) {
            res.send();
        } else if (entity instanceof ParticipantStatus) {
            res.send(((ParticipantStatus) entity).name());
        } else {
            res.send(entity);
        }
    }

    void terminate(@Observes @BeforeDestroyed(ApplicationScoped.class) Object event) {
        exec.shutdown();
        try {
            if (!exec.awaitTermination(300, TimeUnit.MILLISECONDS)) {
                exec.shutdownNow();
            }
        } catch (InterruptedException e) {
            LOGGER.warning("Participant executor shutdown interrupted.");
            exec.shutdownNow();
        }
    }
}
