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
package io.helidon.microprofile.restclient;

import javax.ws.rs.ConstrainedTo;
import javax.ws.rs.RuntimeType;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;

import io.helidon.common.context.Contexts;

/**
 * Server side request filter used for propagation of request headers to server client request.
 * Uses {@link Contexts} to propagate headers.
 */
@ConstrainedTo(RuntimeType.SERVER)
class HelidonRequestHeaderFilter implements ContainerRequestFilter{

    @Override
    public void filter(ContainerRequestContext requestContext) {
        Contexts.context().ifPresent(context -> context.register(new InboundHeaders(requestContext.getHeaders())));
    }

}
