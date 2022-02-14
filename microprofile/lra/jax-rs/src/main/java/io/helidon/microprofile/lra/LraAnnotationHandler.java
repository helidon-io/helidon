/*
 * Copyright (c) 2021, 2022 Oracle and/or its affiliates.
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

import java.lang.reflect.Method;
import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.logging.Logger;

import io.helidon.common.context.Contexts;
import io.helidon.common.reactive.Single;
import io.helidon.lra.coordinator.client.CoordinatorClient;
import io.helidon.lra.coordinator.client.CoordinatorConnectionException;
import io.helidon.lra.coordinator.client.Participant;
import io.helidon.lra.coordinator.client.PropagatedHeaders;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.Response;
import org.jboss.jandex.AnnotationInstance;

import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.LRA_HTTP_CONTEXT_HEADER;
import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.LRA_HTTP_PARENT_CONTEXT_HEADER;
import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.LRA_HTTP_RECOVERY_HEADER;

class LraAnnotationHandler implements AnnotationHandler {

    private static final Logger LOGGER = Logger.getLogger(LraAnnotationHandler.class.getName());

    private final InspectionService.Lra annotation;
    private final CoordinatorClient coordinatorClient;
    private final ParticipantService participantService;
    private Duration coordinatorTimeout;

    LraAnnotationHandler(AnnotationInstance annotation,
                         CoordinatorClient coordinatorClient,
                         InspectionService inspectionService,
                         ParticipantService participantService,
                         Duration coordinatorTimeout) {
        this.participantService = participantService;
        this.coordinatorTimeout = coordinatorTimeout;
        this.annotation = inspectionService.lraAnnotation(annotation);
        this.coordinatorClient = coordinatorClient;
    }

    @Override
    public void handleJaxRsBefore(ContainerRequestContext reqCtx, ResourceInfo resourceInfo) {
        Method method = resourceInfo.getResourceMethod();
        URI baseUri = reqCtx.getUriInfo().getBaseUri();
        PropagatedHeaders propagatedHeaders = participantService.prepareCustomHeaderPropagation(reqCtx.getHeaders());
        Participant participant = participantService.participant(baseUri, resourceInfo.getResourceClass());
        Optional<URI> existingLraId = getLraContext(reqCtx);
        long timeLimit = Duration.of(annotation.timeLimit(), annotation.timeUnit()).toMillis();
        String clientId = method.getDeclaringClass().getName() + "#" + method.getName();



        final URI lraId;
        try {
            switch (annotation.value()) {
                case NESTED:
                    if (existingLraId.isPresent()) {
                        URI parentLraId = existingLraId.get();
                        setParentContext(reqCtx, parentLraId);
                        lraId = start(parentLraId, clientId, propagatedHeaders, timeLimit);
                    } else {
                        lraId = start(clientId, propagatedHeaders, timeLimit);
                    }
                    join(reqCtx, lraId, propagatedHeaders, timeLimit, participant);
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
                        join(reqCtx, lraId, propagatedHeaders, timeLimit, participant);
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
                    // existing lra, fall through to required
                case REQUIRED:
                    if (existingLraId.isPresent()) {
                        lraId = existingLraId.get();
                        join(reqCtx, lraId, propagatedHeaders, timeLimit, participant);
                        setLraContext(reqCtx, lraId);
                        break;
                    }
                    // non-existing lra, fall through to requires_new
                case REQUIRES_NEW:
                    lraId = start(clientId, propagatedHeaders, timeLimit);
                    join(reqCtx, lraId, propagatedHeaders, timeLimit, participant);
                    setLraContext(reqCtx, lraId);
                    break;
                default:
                    LOGGER.severe("Unsupported LRA type " + annotation.value() + " on method " + method.getName());
                    reqCtx.abortWith(Response.status(500).build());
                    break;
            }
        } catch (CoordinatorConnectionException e) {
            throw new WebApplicationException(e.getMessage(), e.getCause(), e.status());
        } finally {
            setHeaderPropagationContext(reqCtx, propagatedHeaders);
        }
    }

    @Override
    public void handleJaxRsAfter(ContainerRequestContext reqCtx,
                                 ContainerResponseContext resCtx,
                                 ResourceInfo resourceInfo) {

        Optional<URI> lraId = Optional.ofNullable((URI) reqCtx.getProperty(LRA_HTTP_CONTEXT_HEADER))
                .or(() -> Contexts.context()
                        .flatMap(c -> c.get(LRA_HTTP_CONTEXT_HEADER, URI.class))
                );

        PropagatedHeaders propagatedHeaders = participantService.prepareCustomHeaderPropagation(reqCtx.getHeaders());

        Response.Status resStatus = resCtx.getStatusInfo().toEnum();
        Response.Status.Family resFamily = resCtx.getStatusInfo().getFamily();
        boolean end = annotation.end();
        boolean cancel = annotation.cancelOnFamily().contains(resFamily)
                || annotation.cancelOn().contains(resStatus);

        lraId.ifPresent(id -> {
            if (cancel) {
                cancel(id, propagatedHeaders);
            } else if (end) {
                close(id, propagatedHeaders);
            }
            resCtx.getHeaders().putSingle(LRA_HTTP_CONTEXT_HEADER, id);
        });

        Optional.ofNullable(reqCtx.getProperty(LRA_HTTP_PARENT_CONTEXT_HEADER))
                .map(URI.class::cast)
                .ifPresent(suppressedLra -> resCtx.getHeaders().putSingle(LRA_HTTP_CONTEXT_HEADER, suppressedLra));
    }

    private URI start(String clientId, PropagatedHeaders headers, long timeOut) {
        return awaitCoordinator(coordinatorClient.start(clientId, headers, timeOut));
    }

    private URI start(URI parentLraId, String clientId, PropagatedHeaders headers, long timeOut) {
        return awaitCoordinator(coordinatorClient.start(parentLraId, clientId, headers, timeOut));
    }

    private void join(ContainerRequestContext reqCtx,
                      URI lraId,
                      PropagatedHeaders propagatedHeaders,
                      long timeLimit,
                      Participant participant) {
        awaitCoordinator(coordinatorClient.join(lraId, propagatedHeaders, timeLimit, participant))
                .ifPresent(uri -> reqCtx.getHeaders().add(LRA_HTTP_RECOVERY_HEADER, uri.toASCIIString()));
    }

    private void close(URI lraId, PropagatedHeaders headers) {
        awaitCoordinator(coordinatorClient.close(lraId, headers));
    }

    private void cancel(URI lraId, PropagatedHeaders headers) {
        awaitCoordinator(coordinatorClient.cancel(lraId, headers));
    }

    private void setHeaderPropagationContext(ContainerRequestContext reqCtx, PropagatedHeaders propagatedHeaders) {
        String key = PropagatedHeaders.class.getName();
        reqCtx.setProperty(key, propagatedHeaders);
        Contexts.context()
                .ifPresent(context -> context.register(key, propagatedHeaders));
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

    private <T> T awaitCoordinator(Single<T> single) {
        try {
            // Connection timeout should be handled by client impl separately
            return single.await(coordinatorTimeout);
        } catch (CompletionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof CoordinatorConnectionException) {
                throw new WebApplicationException(cause.getMessage(), cause.getCause(),
                        ((CoordinatorConnectionException) cause).status());
            } else {
                throw e;
            }
        }
    }
}
