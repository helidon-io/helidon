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

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.UriBuilder;

import io.helidon.common.context.Contexts;

import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.LRA_HTTP_CONTEXT_HEADER;

class NonLraAnnotationHandler implements AnnotationHandler {
    private final Boolean propagate;

    NonLraAnnotationHandler(Boolean propagate) {
        this.propagate = propagate;
    }

    @Override
    public void handleJaxRsBefore(ContainerRequestContext requestContext,
                                  ResourceInfo resourceInfo) {
        // Skip internal resource
        if (resourceInfo.getResourceClass() == ParticipantCdiResource.class) return;

        // not LRA method at all
        if (propagate) {
            // Save lraId from header to thread local for possible clients
            String lraFromHeader = requestContext.getHeaders().getFirst(LRA_HTTP_CONTEXT_HEADER);
            Contexts.context()
                    .ifPresent(c -> c.register(LRA_HTTP_CONTEXT_HEADER, UriBuilder.fromPath(lraFromHeader).build()));
        }
        // clear lra header
        requestContext.getHeaders().remove(LRA_HTTP_CONTEXT_HEADER);
    }

    @Override
    public void handleJaxRsAfter(ContainerRequestContext requestContext,
                                 ContainerResponseContext responseContext,
                                 ResourceInfo resourceInfo) {

    }
}
