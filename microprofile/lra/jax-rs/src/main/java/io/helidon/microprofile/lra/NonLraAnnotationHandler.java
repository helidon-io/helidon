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

import io.helidon.common.context.Contexts;
import io.helidon.lra.coordinator.client.PropagatedHeaders;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.UriBuilder;

import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.LRA_HTTP_CONTEXT_HEADER;

class NonLraAnnotationHandler implements AnnotationHandler {
    private final Boolean propagate;
    private final ParticipantService participantService;

    NonLraAnnotationHandler(Boolean propagate, ParticipantService participantService) {
        this.propagate = propagate;
        this.participantService = participantService;
    }

    @Override
    public void handleJaxRsBefore(ContainerRequestContext requestContext,
                                  ResourceInfo resourceInfo) {
        // not LRA method at all
        if (propagate) {
            // Save lraId from header to thread local for possible clients
            String lraFromHeader = requestContext.getHeaders().getFirst(LRA_HTTP_CONTEXT_HEADER);
            if (lraFromHeader != null && !lraFromHeader.isBlank()) {
                Contexts.context()
                        .ifPresent(c -> c.register(LRA_HTTP_CONTEXT_HEADER, UriBuilder.fromPath(lraFromHeader).build()));
            }
        }
        // clear lra header
        requestContext.getHeaders().remove(LRA_HTTP_CONTEXT_HEADER);

        // Custom headers propagation
        PropagatedHeaders propagatedHeaders = participantService.prepareCustomHeaderPropagation(requestContext.getHeaders());
        String key = PropagatedHeaders.class.getName();
        requestContext.setProperty(key, propagatedHeaders);
        Contexts.context()
                .ifPresent(context -> context.register(key, propagatedHeaders));
    }

    @Override
    public void handleJaxRsAfter(ContainerRequestContext requestContext,
                                 ContainerResponseContext responseContext,
                                 ResourceInfo resourceInfo) {

    }
}
