/*
 * Copyright (c) 2018, 2021 Oracle and/or its affiliates.
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

package io.helidon.security.jwt;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

import io.helidon.common.Errors;
import io.helidon.security.jwt.jwk.JwkRSA;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Unit test for {@link Jwt}.
 */
public class JwtTest {
    private static final Logger LOGGER = Logger.getLogger(JwtTest.class.getName());

    @Test
    public void testBuilderBasicJwt() {
        String id = UUID.randomUUID().toString();
        String audience = "id_of_audience";

        Jwt jwt = Jwt.builder()
                .jwtId(id)
                .algorithm(JwkRSA.ALG_RS256)
                .audience(audience)
                .build();

        assertThat(jwt.jwtId(), is(Optional.of(id)));
        assertThat(jwt.algorithm(), is(Optional.of(JwkRSA.ALG_RS256)));
        assertThat(jwt.audience(), is(Optional.of(List.of(audience))));
    }

    @Test
    public void testOidcJwt() {
        String audience = "id_of_audience";
        String subject = "54564645646465";
        String username = "jarda@jarda.com";
        String issuer = "I am issuer";
        Instant now = Instant.now();
        Instant expiration = now.plus(1, ChronoUnit.HOURS);
        Instant notBefore = now.minus(2, ChronoUnit.SECONDS);

        Jwt jwt = Jwt.builder()
                .jwtId(UUID.randomUUID().toString())
                .addScope("link")
                .addScope("lank")
                .addScope("lunk")
                .subject(subject)
                .preferredUsername(username)
                .algorithm(JwkRSA.ALG_RS256)
                .audience(audience)
                .issuer(issuer)
                // time info
                .issueTime(now)
                .expirationTime(expiration)
                .notBefore(notBefore)
                .build();

        assertThat(jwt.scopes(), is(Optional.of(List.of("link", "lank", "lunk"))));
        assertThat(jwt.subject(), is(Optional.of(subject)));
        assertThat(jwt.preferredUsername(), is(Optional.of(username)));
        assertThat(jwt.issueTime(), is(Optional.of(now)));
        assertThat(jwt.expirationTime(), is(Optional.of(expiration)));
        assertThat(jwt.notBefore(), is(Optional.of(notBefore)));

        //and this one should be valid
        List<Validator<Jwt>> vals = Jwt.defaultTimeValidators();
        Jwt.addIssuerValidator(vals, issuer, true);
        Jwt.addAudienceValidator(vals, audience, true);

        Errors errors = jwt.validate(vals);

        errors.log(LOGGER);
        errors.checkValid();

        //another try with defaults
        errors = jwt.validate(issuer, audience);
        errors.log(LOGGER);
        errors.checkValid();
    }
}
