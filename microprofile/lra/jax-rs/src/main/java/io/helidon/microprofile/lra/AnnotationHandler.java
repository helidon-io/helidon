/*
 * Copyright (c) 2021, 2026 Oracle and/or its affiliates.
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
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;

import io.helidon.common.context.Contexts;
import io.helidon.common.reactive.Single;
import io.helidon.lra.coordinator.client.CoordinatorClient;
import io.helidon.lra.coordinator.client.CoordinatorConnectionException;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ResourceInfo;
import org.jboss.jandex.AnnotationInstance;

import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.LRA_HTTP_CONTEXT_HEADER;

interface AnnotationHandler {

    void handleJaxRsBefore(ContainerRequestContext requestContext,
                           ResourceInfo resourceInfo);

    void handleJaxRsAfter(ContainerRequestContext requestContext,
                          ContainerResponseContext responseContext,
                          ResourceInfo resourceInfo);

    default Optional<URI> getLraContext(ContainerRequestContext reqCtx) {
        return Optional.ofNullable(reqCtx.getHeaders()
                        .get(LRA_HTTP_CONTEXT_HEADER))
                .orElseGet(List::of)
                .stream()
                .findFirst()
                .map(lraContextHeader -> {
                    try {
                        return URI.create(lraContextHeader);
                    } catch (IllegalArgumentException e) {
                        throw new WebApplicationException("Invalid LRA context header", e, 412);
                    }
                })
                .or(() -> Contexts.context()
                        .flatMap(c -> c.get(LRA_HTTP_CONTEXT_HEADER, URI.class)));
    }

    default <T> T awaitCoordinator(Supplier<Single<T>> singleSupplier, Duration coordinatorTimeout) {
        try {
            // Connection timeout should be handled by client impl separately
            return singleSupplier.get().await(coordinatorTimeout);
        } catch (CoordinatorConnectionException e) {
            throw new WebApplicationException(e.getMessage(), e.getCause(), e.status());
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

    @FunctionalInterface
    interface HandlerMaker {
        AnnotationHandler make(AnnotationInstance annotationInstance,
                               CoordinatorClient coordinatorClient,
                               InspectionService inspectionService,
                               ParticipantService participantService,
                               Duration timeout);
    }
}
