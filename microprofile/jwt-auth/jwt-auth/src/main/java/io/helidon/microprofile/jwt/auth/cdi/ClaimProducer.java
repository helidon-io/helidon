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

import java.util.Optional;
import java.util.Set;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import javax.enterprise.inject.spi.InjectionPoint;
import javax.inject.Inject;
import javax.json.Json;
import javax.json.JsonString;
import javax.ws.rs.core.Context;

import io.helidon.common.CollectionsHelper;
import io.helidon.microprofile.jwt.auth.JsonWebTokenImpl;
import io.helidon.security.SecurityContext;
import io.helidon.security.jwt.Jwt;
import io.helidon.security.jwt.SignedJwt;
import io.helidon.security.jwt.jwk.Jwk;

import org.eclipse.microprofile.jwt.Claim;
import org.eclipse.microprofile.jwt.ClaimValue;
import org.eclipse.microprofile.jwt.Claims;
import org.eclipse.microprofile.jwt.JsonWebToken;

/**
 * Class MetricProducer.
 */
@ApplicationScoped
public class ClaimProducer {

    @Inject
    @Impl
    private JsonWebTokenImpl token;

    @Produces
    @Claim
    public String produceClaim(InjectionPoint ip) {

        Claim claim = ip.getAnnotated().getAnnotation(Claim.class);
        if (claim.standard() == Claims.UNKNOWN) {
            return token.getClaim(claim.value());
        } else {
            return token.getClaim(claim.standard().name());
        }
    }

    @Produces
    @Claim
    public JsonString produceJsonClaim(InjectionPoint ip) {
        Claim claim = ip.getAnnotated().getAnnotation(Claim.class);
        return Json.createValue("Injected claim");
    }

    @Produces
    @Claim
    public Optional<String> produceOptionalClaim(InjectionPoint ip) {
        return Optional.of("OptionalString");
    }

    @Produces
    @Claim
    public Optional<ClaimValue<Set<String>>> produceClaimValueSet(InjectionPoint ip) {
        Claim claim = ip.getAnnotated().getAnnotation(Claim.class);
        return Optional.of(new ClaimValue<Set<String>>() {
            @Override
            public String getName() {
                return "name";
            }

            @Override
            public Set<String> getValue() {
                return CollectionsHelper.setOf("name");
            }
        });
    }

    @Produces
    @Claim
    public Optional<ClaimValue<Set<JsonString>>> produceClaimValueSetJson(InjectionPoint ip) {
        Claim claim = ip.getAnnotated().getAnnotation(Claim.class);
        return Optional.empty();
    }


}
