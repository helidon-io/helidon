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
import java.util.List;
import java.util.Optional;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ResourceInfo;


import io.helidon.common.context.Contexts;
import io.helidon.lra.coordinator.client.CoordinatorClient;

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
                .map(URI::create)
                .or(() -> Contexts.context()
                        .flatMap(c -> c.get(LRA_HTTP_CONTEXT_HEADER, URI.class)));
    }

    @FunctionalInterface
    interface HandlerMaker {
        AnnotationHandler make(AnnotationInstance annotationInstance,
                               CoordinatorClient coordinatorClient,
                               InspectionService inspectionService,
                               ParticipantService participantService);
    }
}
