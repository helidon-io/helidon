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

import java.util.concurrent.atomic.AtomicLong;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.RequestScoped;
import javax.enterprise.inject.Produces;

import io.helidon.common.context.Contexts;
import io.helidon.security.Security;
import io.helidon.security.SecurityContext;

/**
 * A bean for injection of security context.
 */
class SecurityProducer {
    private final AtomicLong contextCounter = new AtomicLong();

    SecurityProducer() {
    }

    @ApplicationScoped
    @Produces
    Security security() {
        return Contexts.context()
                .flatMap(context -> context.get(Security.class))
                .orElseThrow(() -> new IllegalStateException("Security cannot be injected when not configured"));
    }

    @RequestScoped
    @Produces
    SecurityContext securityContext(Security security) {
        return Contexts.context()
                .flatMap(context -> context.get(SecurityContext.class))
                .orElseGet(() -> emptyContext(security));
    }

    private SecurityContext emptyContext(Security security) {
        return security.createContext("security-producer-context-" + contextCounter.incrementAndGet());
    }
}
