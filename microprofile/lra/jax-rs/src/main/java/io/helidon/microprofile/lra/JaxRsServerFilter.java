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

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;

import jakarta.inject.Inject;
import jakarta.ws.rs.ConstrainedTo;
import jakarta.ws.rs.RuntimeType;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.Context;

@ConstrainedTo(RuntimeType.SERVER)
class JaxRsServerFilter implements ContainerRequestFilter, ContainerResponseFilter {

    private static final Logger LOGGER = Logger.getLogger(JaxRsServerFilter.class.getName());

    @Context
    private ResourceInfo resourceInfo;

    @Inject
    private HandlerService handlerService;

    @Override
    public void filter(ContainerRequestContext reqCtx) throws IOException {
        try {
            Method method = resourceInfo.getResourceMethod();

            // select proper lra annotation handlers and process
            for (var handler : handlerService.getHandlers(method)) {
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
}
