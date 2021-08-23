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

import java.lang.reflect.Method;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.Response;

import io.helidon.common.context.Contexts;
import io.helidon.lra.coordinator.client.CoordinatorClient;
import io.helidon.lra.coordinator.client.CoordinatorConnectionException;
import io.helidon.lra.coordinator.client.Participant;

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
        Method method = resourceInfo.getResourceMethod();
        URI baseUri = reqCtx.getUriInfo().getBaseUri();
        Participant participant = participantService.participant(baseUri, resourceInfo.getResourceClass());
        Optional<URI> existingLraId = Contexts.context().flatMap(c -> c.get(LRA_HTTP_CONTEXT_HEADER, URI.class));
        long timeLimit = Duration.of(annotation.timeLimit(), annotation.timeUnit()).toMillis();
        String clientId = method.getDeclaringClass().getName() + "#" + method.getName();

        final URI lraId;
        try {
            switch (annotation.value()) {
                case NESTED:
                    if (existingLraId.isPresent()) {
                        URI parentLraId = existingLraId.get();
                        setParentContext(reqCtx, parentLraId);
                        lraId = coordinatorClient.start(parentLraId, clientId, timeLimit);
                    } else {
                        lraId = coordinatorClient.start(clientId, timeLimit);
                    }
                    join(reqCtx, lraId, timeLimit, participant);
                    setLraContext(reqCtx, lraId);
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
                        lraId = existingLraId.get();
                        join(reqCtx, lraId, timeLimit, participant);
                        setLraContext(reqCtx, lraId);
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
                        lraId = existingLraId.get();
                        join(reqCtx, lraId, timeLimit, participant);
                        setLraContext(reqCtx, lraId);
                        break;
                    }
                    // non existing lra, fall thru to requires_new
                case REQUIRES_NEW:
                    lraId = coordinatorClient.start(clientId, timeLimit);
                    join(reqCtx, lraId, timeLimit, participant);
                    setLraContext(reqCtx, lraId);
                    break;
                default:
                    LOGGER.severe("Unsupported LRA type " + annotation.value() + " on method " + method.getName());
                    reqCtx.abortWith(Response.status(500).build());
                    break;
            }
        } catch (CoordinatorConnectionException e) {
            throw new WebApplicationException(e.getMessage(), e.getCause(), e.status());
        }
    }

    @Override
    public void handleJaxRsAfter(ContainerRequestContext reqCtx,
                                 ContainerResponseContext resCtx,
                                 ResourceInfo resourceInfo) {
        Optional<URI> lraId = Optional.ofNullable((URI) reqCtx.getProperty(LRA_HTTP_CONTEXT_HEADER))
                .or(() -> Contexts.context().flatMap(c -> c.get(LRA_HTTP_CONTEXT_HEADER, URI.class)));

        boolean end = annotation.end();
        Set<Response.Status.Family> cancelOnFamilies = annotation.cancelOnFamily();
        Set<Response.Status> cancelOnStatuses;
        cancelOnStatuses = annotation.cancelOn();

        if (lraId.isPresent()
                && (cancelOnFamilies.contains(resCtx.getStatusInfo().getFamily())
                || cancelOnStatuses.contains(resCtx.getStatusInfo().toEnum()))) {
            coordinatorClient.cancel(lraId.get());
        } else if (lraId.isPresent() && end) {
            coordinatorClient.close(lraId.get());
        }
        URI suppressedLra = (URI) reqCtx.getProperty(LRA_HTTP_PARENT_CONTEXT_HEADER);
        if (suppressedLra != null) {
            resCtx.getHeaders().putSingle(LRA_HTTP_CONTEXT_HEADER, suppressedLra.toASCIIString());
        }

        lraId.ifPresent(uri -> resCtx.getHeaders().putIfAbsent(LRA_HTTP_CONTEXT_HEADER, List.of(uri.toASCIIString())));
    }

    private void join(ContainerRequestContext reqCtx, URI lraId, long timeLimit, Participant participant) {
        coordinatorClient.join(lraId, timeLimit, participant)
                .map(URI::toASCIIString)
                .ifPresent(uri -> reqCtx.getHeaders().add(LRA_HTTP_RECOVERY_HEADER, uri));
    }

    private void setLraContext(ContainerRequestContext reqCtx, URI lraId) {
        reqCtx.getHeaders().putSingle(LRA_HTTP_CONTEXT_HEADER, lraId.toASCIIString());
        reqCtx.setProperty(LRA_HTTP_CONTEXT_HEADER, lraId);
        Contexts.context()
                .ifPresent(context -> context.register(LRA_HTTP_CONTEXT_HEADER, lraId));
    }

    private void setParentContext(ContainerRequestContext reqCtx, URI lraId) {
        reqCtx.getHeaders().putSingle(LRA_HTTP_PARENT_CONTEXT_HEADER, lraId.toASCIIString());
        reqCtx.setProperty(LRA_HTTP_PARENT_CONTEXT_HEADER, lraId);
        Contexts.context()
                .ifPresent(context -> context.register(LRA_HTTP_PARENT_CONTEXT_HEADER, lraId));
    }
}
