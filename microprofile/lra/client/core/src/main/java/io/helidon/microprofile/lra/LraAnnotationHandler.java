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

package io.helidon.microprofile.lra;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.Response;

import io.helidon.microprofile.lra.coordinator.client.CoordinatorClient;

import org.jboss.jandex.AnnotationInstance;

import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.LRA_HTTP_CONTEXT_HEADER;
import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.LRA_HTTP_PARENT_CONTEXT_HEADER;
import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.LRA_HTTP_RECOVERY_HEADER;

class LraAnnotationHandler implements AnnotationHandler {

    private static final Logger LOGGER = Logger.getLogger(LraAnnotationHandler.class.getName());

    private final InspectionService.Lra annotation;
    private final CoordinatorClient coordinatorClient;
    private final ParticipantService participantService;

    LraAnnotationHandler(AnnotationInstance annotation,
                         CoordinatorClient coordinatorClient,
                         InspectionService inspectionService,
                         ParticipantService participantService) {
        this.participantService = participantService;
        this.annotation = inspectionService.lraAnnotation(annotation);
        this.coordinatorClient = coordinatorClient;
    }

    @Override
    public void handleJaxRsBefore(ContainerRequestContext reqCtx, ResourceInfo resourceInfo) {
        var method = resourceInfo.getResourceMethod();
        var baseUri = reqCtx.getUriInfo().getBaseUri();
        var participant = participantService.participant(baseUri, resourceInfo.getResourceClass());
        var existingLraId = LraThreadContext.get().lra();
        var timeLimit = Duration.of(annotation.timeLimit(), annotation.timeUnit()).toMillis();
        var clientId = method.getDeclaringClass().getName() + "#" + method.getName();

        URI lraId = null;
        URI parentLraId = null;
        switch (annotation.value()) {
            case NESTED:
                if (existingLraId.isPresent()) {
                    parentLraId = existingLraId.get();
                    reqCtx.getHeaders().putSingle(LRA_HTTP_PARENT_CONTEXT_HEADER, existingLraId.get().toASCIIString());
                    lraId = coordinatorClient.start(existingLraId.get(), clientId, timeLimit);
                    coordinatorClient.join(lraId, timeLimit, participant)
                            .map(URI::toASCIIString)
                            .ifPresent(uri -> reqCtx.getHeaders().add(LRA_HTTP_RECOVERY_HEADER, uri));
                } else {
                    lraId = coordinatorClient.start(null, clientId, timeLimit);
                    coordinatorClient.join(lraId, timeLimit, participant)
                            .map(URI::toASCIIString)
                            .ifPresent(uri -> reqCtx.getHeaders().add(LRA_HTTP_RECOVERY_HEADER, uri));
                }
                break;
            case NEVER:
                if (existingLraId.isPresent()) {
                    // If called inside an LRA context, i.e., the method is not executed
                    // and a 412 Precondition Failed is returned
                    reqCtx.abortWith(Response.status(Response.Status.PRECONDITION_FAILED).build());
                    return;
                }
                break;
            case NOT_SUPPORTED:
                reqCtx.getHeaders().remove(LRA_HTTP_CONTEXT_HEADER);
                return;
            case SUPPORTS:
                if (existingLraId.isPresent()) {
                    coordinatorClient.join(existingLraId.get(), timeLimit, participant)
                            .map(URI::toASCIIString)
                            .ifPresent(uri -> reqCtx.getHeaders().add(LRA_HTTP_RECOVERY_HEADER, uri));
                    lraId = existingLraId.get();
                    break;
                }
                break;
            case MANDATORY:
                if (existingLraId.isEmpty()) {
                    // If called outside an LRA context the method is not executed and a
                    // 412 Precondition Failed HTTP status code is returned to the caller
                    reqCtx.abortWith(Response.status(Response.Status.PRECONDITION_FAILED).build());
                    return;
                }
                // existing lra, fall thru to required
            case REQUIRED:
                if (existingLraId.isPresent()) {
                    coordinatorClient.join(existingLraId.get(), timeLimit, participant)
                            .map(URI::toASCIIString)
                            .ifPresent(uri -> reqCtx.getHeaders().add(LRA_HTTP_RECOVERY_HEADER, uri));
                    lraId = existingLraId.get();
                    break;
                }
                // non existing lra, fall thru to requires_new
            case REQUIRES_NEW:
                lraId = coordinatorClient.start(null, clientId, timeLimit);
                coordinatorClient.join(lraId, timeLimit, participant)
                        .map(URI::toASCIIString)
                        .ifPresent(uri -> reqCtx.getHeaders().add(LRA_HTTP_RECOVERY_HEADER, uri));
                break;
            default:
                LOGGER.severe("Unsupported LRA type " + annotation.value() + " on method " + method.getName());
                reqCtx.abortWith(Response.status(500).build());
        }
        lraId = lraId != null ? lraId : existingLraId.orElse(null);
        if (lraId != null) {
            reqCtx.getHeaders().putSingle(LRA_HTTP_CONTEXT_HEADER, lraId.toASCIIString());
            LraThreadContext.get().lra(lraId);
            reqCtx.setProperty(LRA_HTTP_CONTEXT_HEADER, lraId);
        }
        if (parentLraId != null) {
            reqCtx.getHeaders().putSingle(LRA_HTTP_PARENT_CONTEXT_HEADER, parentLraId.toASCIIString());
            reqCtx.setProperty(LRA_HTTP_PARENT_CONTEXT_HEADER, parentLraId);
        }
    }

    @Override
    public void handleJaxRsAfter(ContainerRequestContext requestContext,
                                 ContainerResponseContext responseContext,
                                 ResourceInfo resourceInfo) {
        Optional<URI> lraId = Optional.ofNullable((URI) requestContext.getProperty(LRA_HTTP_CONTEXT_HEADER))
                .or(() -> LraThreadContext.get().lra());

        var end = annotation.end();
        var cancelOnFamilies = annotation.cancelOnFamily();
        var cancelOnStatuses = annotation.cancelOn();

        if (lraId.isPresent()
                && (cancelOnFamilies.contains(responseContext.getStatusInfo().getFamily())
                || cancelOnStatuses.contains(responseContext.getStatusInfo().toEnum()))) {
            coordinatorClient.cancel(lraId.get());
        } else if (lraId.isPresent() && end) {
            coordinatorClient.close(lraId.get());
        }
        URI suppressedLra = (URI) requestContext.getProperty(LRA_HTTP_PARENT_CONTEXT_HEADER);
        if (suppressedLra != null) {
            responseContext.getHeaders().putSingle(LRA_HTTP_CONTEXT_HEADER, suppressedLra.toASCIIString());
        }

        if (lraId.isPresent()) {
            responseContext.getHeaders().putIfAbsent(LRA_HTTP_CONTEXT_HEADER, List.of(lraId.get().toASCIIString()));
        }
    }
}
