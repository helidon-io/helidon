/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.microprofile.security;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;

import io.helidon.common.context.Contexts;
import io.helidon.security.Security;
import io.helidon.security.SecurityContext;

import jakarta.annotation.Priority;
import jakarta.ws.rs.ConstrainedTo;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.RuntimeType;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.GenericType;
import org.glassfish.jersey.internal.inject.InjectionManager;
import org.glassfish.jersey.internal.util.collection.Ref;

/**
 * Empty security context creator.
 * <br/>
 *
 * When security is disabled, null value would be injected. This filter ensures injection of an empty context.
 */
@SuppressWarnings("CdiManagedBeanInconsistencyInspection")
@PreMatching
@Priority(Priorities.AUTHENTICATION)
@ConstrainedTo(RuntimeType.SERVER)
class SecurityDisabledFilter implements ContainerRequestFilter {

    private final AtomicLong contextCounter = new AtomicLong();

    private final Security security;

    SecurityDisabledFilter(Security security) {
        this.security = security;
    }

    @Context
    private InjectionManager injectionManager;

    @Override
    public void filter(ContainerRequestContext containerRequestContext) throws IOException {
        // create a new security context
        SecurityContext securityContext = security.createContext("empty-security-context-" + contextCounter.incrementAndGet());

        Contexts.context().ifPresent(ctx -> ctx.register(securityContext));

        injectionManager.<Ref<SecurityContext>>getInstance((new GenericType<Ref<SecurityContext>>() { }).getType())
                .set(securityContext);
    }
}
