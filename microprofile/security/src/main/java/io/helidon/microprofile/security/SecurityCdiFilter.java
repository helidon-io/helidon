/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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

import javax.annotation.Priority;
import javax.enterprise.inject.Instance;
import javax.enterprise.inject.spi.CDI;
import javax.ws.rs.Priorities;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;

import io.helidon.common.context.Contexts;
import io.helidon.security.SecurityContext;

/**
 * This filter adds security context for CDI injection.
 */
@Priority(Priorities.AUTHENTICATION + 1)
class SecurityCdiFilter implements ContainerRequestFilter {
    @Override
    public void filter(ContainerRequestContext requestContext) {
        Contexts.context()
                .flatMap(context -> context.get(SecurityContext.class))
                .ifPresent(context -> {
                    Instance<SecurityContextProvider> select = CDI.current().select(SecurityContextProvider.class);
                    for (SecurityContextProvider found : select) {
                        found.securityContext(context);
                    }
                });
    }
}
