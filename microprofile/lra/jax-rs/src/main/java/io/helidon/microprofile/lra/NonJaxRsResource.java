/*
 * Copyright (c) 2021, 2023 Oracle and/or its affiliates.
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

import java.lang.System.Logger.Level;
import java.net.URI;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import io.helidon.common.Reflected;
import io.helidon.common.parameters.Parameters;
import io.helidon.config.Config;
import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.HttpPrologue;
import io.helidon.http.ServerRequestHeaders;
import io.helidon.http.Status;
import io.helidon.lra.coordinator.client.PropagatedHeaders;
import io.helidon.webserver.http.HttpService;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.lra.LRAResponse;
import org.eclipse.microprofile.lra.annotation.LRAStatus;
import org.eclipse.microprofile.lra.annotation.ParticipantStatus;
import org.eclipse.microprofile.lra.annotation.ws.rs.LRA;

@Reflected
class NonJaxRsResource {

    static final String CONFIG_CONTEXT_KEY = "lra.participant.non-jax-rs";
    static final String CONFIG_CONTEXT_PATH_KEY = CONFIG_CONTEXT_KEY + ".context-path";
    static final String CONTEXT_PATH_DEFAULT = "/lra-participant";

    private static final System.Logger LOGGER = System.getLogger(NonJaxRsResource.class.getName());
    private static final String LRA_PARTICIPANT = "lra-participant";
    private static final HeaderName LRA_HTTP_CONTEXT_HEADER = HeaderNames.create(LRA.LRA_HTTP_CONTEXT_HEADER);
    private static final HeaderName LRA_HTTP_ENDED_CONTEXT_HEADER = HeaderNames.create(LRA.LRA_HTTP_ENDED_CONTEXT_HEADER);
    private static final HeaderName LRA_HTTP_PARENT_CONTEXT_HEADER = HeaderNames.create(LRA.LRA_HTTP_PARENT_CONTEXT_HEADER);
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
    private final String contextPath;

    @Inject
    NonJaxRsResource(ParticipantService participantService,
                     @ConfigProperty(name = CONFIG_CONTEXT_PATH_KEY,
                                     defaultValue = CONTEXT_PATH_DEFAULT) String contextPath,
                     Config config) {
        this.participantService = participantService;
        this.contextPath = contextPath;
    }

    String contextPath() {
        return contextPath;
    }

    HttpService createNonJaxRsParticipantResource() {
        return rules -> rules
                .any("/{type}/{fqdn}/{methodName}", this::handleRequest);
    }

    private void handleRequest(ServerRequest req, ServerResponse res) {
        HttpPrologue prologue = req.prologue();

        if (LOGGER.isLoggable(Level.DEBUG)) {
            LOGGER.log(Level.DEBUG, "Non JAX-RS LRA resource " + prologue.method().text()
                    + " " + req.path().absolute().path());
        }
        ServerRequestHeaders headers = req.headers();
        Parameters path = req.path().pathParameters();

        URI lraId = headers.first(LRA_HTTP_CONTEXT_HEADER)
                .or(() -> headers.first(LRA_HTTP_ENDED_CONTEXT_HEADER))
                .map(URI::create)
                .orElse(null);

        URI parentId = headers.first(LRA_HTTP_PARENT_CONTEXT_HEADER)
                .map(URI::create)
                .orElse(null);

        PropagatedHeaders propagatedHeaders = participantService.prepareCustomHeaderPropagation(headers.toMap());

        String fqdn = path.get("fqdn");
        String method = path.get("methodName");
        String type = path.get("type");

        try {
            handleRequest(req, res, type, fqdn, method, lraId, parentId, propagatedHeaders);
        } catch (Exception e) {
            sendError(lraId, req, res, e);
        }
    }

    @SuppressWarnings("checkstyle:ParameterNumber") // all parameters required, no benefit using a record wrapper
    private void handleRequest(ServerRequest req,
                               ServerResponse res,
                               String type,
                               String fqdn,
                               String method,
                               URI lraId,
                               URI parentId,
                               PropagatedHeaders propagatedHeaders) {
        switch (type) {
        case "compensate", "complete", "forget" -> {
            Optional<?> result = participantService.invoke(fqdn, method, lraId, parentId, propagatedHeaders);
            result.ifPresentOrElse(r -> sendResult(res, r),
                                   res::send);
        }
        case "afterlra" -> {
            LRAStatus status = LRAStatus.valueOf(req.content().as(String.class));
            Optional<?> result = participantService.invoke(fqdn, method, lraId, status, propagatedHeaders);
            result.ifPresentOrElse(r -> sendResult(res, r),
                                         res::send);
        }
        case "status" -> {
            Optional<?> result = participantService.invoke(fqdn, method, lraId, null, propagatedHeaders);
            result.ifPresentOrElse(
                    r -> sendResult(res, r),
                    // If the participant has already responded successfully
                    // to a @Compensate or @Complete method invocation
                    // then it MAY report 410 Gone HTTP status code
                    // or in the case of non-JAX-RS method returning ParticipantStatus null.
                    () -> res.status(Status.GONE_410).send());
        }
        default -> {
            LOGGER.log(Level.ERROR, "Unexpected non Jax-Rs LRA compensation type "
                    + type + ": " + req.path().absolute().path());
            res.status(Status.NOT_FOUND_404).send();
        }
        }
    }

    private void sendError(URI lraId, ServerRequest req, ServerResponse res, Throwable t) {
        if (LOGGER.isLoggable(Level.DEBUG)) {
            LOGGER.log(Level.DEBUG, "Non Jax-Rs LRA participant resource "
                               + req.path().absolute().path()
                               + " responds with error."
                               + "LRA id: " + lraId,
                       t);
        }
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
        res.status(Status.create(response.getStatus()));
        response.getHeaders()
                .forEach((k, values) -> res.header(HeaderNames.create(k),
                                                   values.stream()
                                                           .map(String::valueOf)
                                                           .toArray(String[]::new)));
        Object entity = response.getEntity();
        if (entity == null) {
            res.send();
        } else if (entity instanceof ParticipantStatus) {
            res.send(((ParticipantStatus) entity).name());
        } else {
            res.send(entity);
        }
    }
}
