/*
 * Copyright (c) 2018, 2023 Oracle and/or its affiliates.
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

import io.helidon.security.jwt.Jwt;
import io.helidon.security.jwt.SignedJwt;
import io.helidon.security.jwt.jwk.Jwk;

import org.eclipse.microprofile.jwt.Claims;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

/**
 * Unit test for {@link JsonWebTokenImpl}.
 */
class JsonWebTokenImplTest {
    @Test
    void testUpnFromSub() {
        String name = "me@example.org";
        Jwt jwt = Jwt.builder()
                .subject(name)
                .build();
        SignedJwt signed = SignedJwt.sign(jwt, Jwk.NONE_JWK);

        JsonWebTokenImpl impl = JsonWebTokenImpl.create(signed);

        assertThat(impl.getName(), is(name));
        assertThat(impl.getClaim(Claims.upn.name()), is(name));
    }

    @Test
    void testUpnFromPreferred() {
        String subject = "123456";
        String name = "me@example.org";
        Jwt jwt = Jwt.builder()
                .subject(subject)
                .preferredUsername(name)
                .build();
        SignedJwt signed = SignedJwt.sign(jwt, Jwk.NONE_JWK);

        JsonWebTokenImpl impl = JsonWebTokenImpl.create(signed);

        assertThat(impl.getName(), is(name));
        assertThat(impl.getClaim(Claims.upn.name()), is(name));
    }

    @Test
    void testUpnFromUpn() {
        String subject = "123456";
        String preferred = "Me Surname";
        String name = "me@example.org";
        Jwt jwt = Jwt.builder()
                .subject(subject)
                .preferredUsername(preferred)
                .userPrincipal(name)
                .build();
        SignedJwt signed = SignedJwt.sign(jwt, Jwk.NONE_JWK);

        JsonWebTokenImpl impl = JsonWebTokenImpl.create(signed);

        assertThat(impl.getName(), is(name));
        assertThat(impl.getClaim(Claims.upn.name()), is(name));
    }

    @Test
    void testGetClaim() {
        Jwt jwt = Jwt.builder()
                .issuer("issuer")
                .subject("subject")
                .addUserGroup("users")
                .addUserGroup("admins")
                .build();

        SignedJwt signed = SignedJwt.sign(jwt, Jwk.NONE_JWK);

        JsonWebTokenImpl impl = JsonWebTokenImpl.create(signed);

        assertAll(
                () -> testClaimType(impl, Claims.sub),
                () -> testClaimType(impl, Claims.groups),
                () -> testClaimType(impl, Claims.iss)
        );
    }

    private void testClaimType(JsonWebToken token, Claims claims) {
        Object claim = token.getClaim(claims.name());
        assertThat("Claim " + claims.name() + " type check", claim, instanceOf(claims.getType()));
    }
}