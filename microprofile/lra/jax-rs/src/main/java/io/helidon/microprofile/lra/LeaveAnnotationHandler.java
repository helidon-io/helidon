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

import java.net.URI;

import io.helidon.common.context.Contexts;
import io.helidon.lra.coordinator.client.CoordinatorClient;
import io.helidon.lra.coordinator.client.Participant;
import io.helidon.lra.coordinator.client.PropagatedHeaders;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ResourceInfo;

import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.LRA_HTTP_CONTEXT_HEADER;

class LeaveAnnotationHandler implements AnnotationHandler {

    private final CoordinatorClient coordinatorClient;
    private final ParticipantService participantService;

    LeaveAnnotationHandler(CoordinatorClient coordinatorClient, ParticipantService participantService) {
        this.coordinatorClient = coordinatorClient;
        this.participantService = participantService;
    }

    @Override
    public void handleJaxRsBefore(ContainerRequestContext reqCtx, ResourceInfo resourceInfo) {
        getLraContext(reqCtx)
                .ifPresent(lraId -> {
                    URI baseUri = reqCtx.getUriInfo().getBaseUri();
                    Participant p = participantService.participant(baseUri, resourceInfo.getResourceClass());
                    coordinatorClient.leave(lraId, PropagatedHeaders.noop(), p);
                    reqCtx.getHeaders().add(LRA_HTTP_CONTEXT_HEADER, lraId.toASCIIString());
                    reqCtx.setProperty(LRA_HTTP_CONTEXT_HEADER, lraId);
                });

        // Custom headers propagation
        PropagatedHeaders propagatedHeaders = participantService.prepareCustomHeaderPropagation(reqCtx.getHeaders());
        String key = PropagatedHeaders.class.getName();
        reqCtx.setProperty(key, propagatedHeaders);
        Contexts.context()
                .ifPresent(context -> context.register(key, propagatedHeaders));
    }

    @Override
    public void handleJaxRsAfter(final ContainerRequestContext requestContext,
                                 ContainerResponseContext responseContext,
                                 ResourceInfo resourceInfo) {

    }
}
