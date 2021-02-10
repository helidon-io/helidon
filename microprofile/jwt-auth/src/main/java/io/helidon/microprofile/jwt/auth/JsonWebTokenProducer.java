/*
 * Copyright (c) 2018, 2021 Oracle and/or its affiliates. All rights reserved.
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

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.RequestScoped;
import javax.enterprise.inject.Produces;
import javax.inject.Inject;

import io.helidon.security.SecurityContext;

import org.eclipse.microprofile.jwt.JsonWebToken;

/**
 * Producer of JsonWebTokenImpl for CDI.
 */
@ApplicationScoped
class JsonWebTokenProducer {

    @Inject
    private SecurityContext securityContext;

    @Produces
    @RequestScoped
    public JsonWebToken produceToken() {
        return securityContext.userPrincipal()
                .map(JsonWebToken.class::cast)
                .orElseGet(JsonWebTokenImpl::empty);
    }

    @Produces
    @Impl
    @RequestScoped
    public JsonWebTokenImpl produceTokenImpl() {
        return (JsonWebTokenImpl) produceToken();
    }
}
