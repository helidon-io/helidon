/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.microprofile.jwt.auth.cdi;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import javax.enterprise.inject.spi.InjectionPoint;
import javax.ws.rs.core.Context;

import io.helidon.microprofile.jwt.auth.JsonWebTokenImpl;
import io.helidon.security.SecurityContext;
import io.helidon.security.jwt.Jwt;
import io.helidon.security.jwt.SignedJwt;
import io.helidon.security.jwt.jwk.Jwk;

import org.eclipse.microprofile.jwt.JsonWebToken;

/**
 * TODO javadoc.
 */
@ApplicationScoped
public class JsonWebTokenProducer {
    @Context
    private SecurityContext securityContext;

    @Produces
    public JsonWebToken produceToken(InjectionPoint ip) {
        Jwt jwt = Jwt.builder().subject("TypekLibovej").build();
        SignedJwt signedJwt = SignedJwt.sign(jwt, Jwk.NONE_JWK);
        return new JsonWebTokenImpl(jwt, signedJwt);
    }

    @Produces
    @Impl
    public JsonWebTokenImpl produceTokenImpl(InjectionPoint ip) {
        Jwt jwt = Jwt.builder().subject("TypekLibovej").build();
        SignedJwt signedJwt = SignedJwt.sign(jwt, Jwk.NONE_JWK);
        return new JsonWebTokenImpl(jwt, signedJwt);
    }
}
