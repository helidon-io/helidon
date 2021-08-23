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

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.inject.Inject;
import javax.ws.rs.ConstrainedTo;
import javax.ws.rs.RuntimeType;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.Context;

import io.helidon.common.context.Contexts;
import io.helidon.lra.coordinator.client.CoordinatorClient;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.LRA_HTTP_CONTEXT_HEADER;

@ConstrainedTo(RuntimeType.SERVER)
class JaxRsServerFilter implements ContainerRequestFilter, ContainerResponseFilter {

    private static final Logger LOGGER = Logger.getLogger(JaxRsServerFilter.class.getName());

    @Context
    private ResourceInfo resourceInfo;

    @Inject
    private CoordinatorClient coordinatorClient;

    @Inject
    private HandlerService handlerService;

    @ConfigProperty(name = "mp.lra.propagation.active", defaultValue = "true")
    private boolean propagate;

    @Override
    public void filter(ContainerRequestContext reqCtx) throws IOException {
        try {
            Method method = resourceInfo.getResourceMethod();
            List<AnnotationHandler> lraHandlers = handlerService.getHandlers(method);

            if (propagate || !lraHandlers.isEmpty()) {
                // if propagate for non lra endpoints is on or method is LRA resource
                setLraContext(reqCtx);
            }

            // select proper lra annotation handlers and process
            for (var handler : lraHandlers) {
                handler.handleJaxRsBefore(reqCtx, resourceInfo);
            }
        } catch (WebApplicationException e) {
            // Rethrow error responses
            throw e;
        } catch (Throwable e) {
            LOGGER.log(Level.SEVERE, "Error when invoking LRA participant request", e);
        }
    }

    @Override
    public void filter(ContainerRequestContext requestContext,
                       ContainerResponseContext responseContext) throws IOException {
        try {
            Method method = resourceInfo.getResourceMethod();
            if (method == null) {
                return;
            }

            // select current lra annotation handler and process
            for (var handler : handlerService.getHandlers(method)) {
                handler.handleJaxRsAfter(requestContext, responseContext, resourceInfo);
            }
        } catch (Throwable t) {
            LOGGER.log(Level.SEVERE, "Error in after LRA filter", t);
        }
    }

    private void setLraContext(ContainerRequestContext reqCtx) {
        Optional.ofNullable(reqCtx.getHeaders().getFirst(LRA_HTTP_CONTEXT_HEADER))
                .map(URI::create)
                .ifPresent(lraId -> Contexts.context()
                        .orElseThrow(() -> new IllegalStateException("LRA Jax-Rs resource executed out of Helidon context."))
                        .register(LRA_HTTP_CONTEXT_HEADER, lraId));
    }
}
