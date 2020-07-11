/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates.
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

import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.RequestScoped;
import javax.enterprise.inject.Produces;
import javax.enterprise.inject.spi.BeanManager;

import io.helidon.common.context.Context;
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
    Security security(BeanManager beanManager) {
        return Contexts.context()
                .flatMap(this::security)
                .orElseGet(() -> securityFromExtension(beanManager));
    }

    @RequestScoped
    @Produces
    SecurityContext securityContext(BeanManager beanManager) {
        return Contexts.context()
                .flatMap(this::securityContext)
                .orElseGet(() -> emptyContext(security(beanManager)));
    }

    private Optional<SecurityContext> securityContext(Context context) {
        return context.get(SecurityContext.class);
    }

    private Optional<Security> security(Context context) {
        return context.get(Security.class);
    }

    private SecurityContext emptyContext(Security security) {
        return security.createContext("security-producer-context-" + contextCounter.incrementAndGet());
    }

    private Security securityFromExtension(BeanManager beanManager) {
        return beanManager.getExtension(SecurityCdiExtension.class)
                .security()
                .orElseThrow(() -> new IllegalStateException("Security cannot be injected when not configured"));
    }
}
