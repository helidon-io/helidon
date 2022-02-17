/*
 * Copyright (c) 2018, 2022 Oracle and/or its affiliates.
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
package io.helidon.microprofile.jwt.auth;

import io.helidon.security.Principal;
import io.helidon.security.SecurityContext;
import io.helidon.security.Subject;
import io.helidon.security.jwt.SignedJwt;
import io.helidon.security.providers.common.TokenCredential;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.eclipse.microprofile.jwt.JsonWebToken;

/**
 * Producer of JsonWebTokenImpl for CDI.
 */
// must be in RequestScoped - ApplicationScoped fails some tests
@ApplicationScoped
class JsonWebTokenProducer {
    @Inject
    private SecurityContext securityContext;

    @Produces
    @RequestScoped
    public JsonWebToken produceToken() {
        return securityContext.user()
                .map(this::toJsonWebToken)
                .orElseGet(JsonWebTokenImpl::empty);
    }

    private JsonWebTokenImpl toJsonWebToken(Subject subject) {
        Principal principal = subject.principal();
        if (principal instanceof JsonWebTokenImpl) {
            return (JsonWebTokenImpl) principal;
        }
        return subject.publicCredential(TokenCredential.class)
                .flatMap(it -> it.getTokenInstance(SignedJwt.class))
                .map(JsonWebTokenImpl::create)
                .orElseGet(JsonWebTokenImpl::empty);
    }

    @Produces
    @Impl
    @RequestScoped
    public JsonWebTokenImpl produceTokenImpl() {
        return (JsonWebTokenImpl) produceToken();
    }
}
