/*
 * Copyright (c) 2018, 2026 Oracle and/or its affiliates.
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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import io.helidon.common.Errors;
import io.helidon.json.JsonArray;
import io.helidon.json.JsonNull;
import io.helidon.json.JsonObject;
import io.helidon.json.JsonString;
import io.helidon.security.jwt.jwk.JwkRSA;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit test for {@link Jwt}.
 */
public class JwtTest {
    private static final System.Logger LOGGER = System.getLogger(JwtTest.class.getName());

    @Test
    public void testBuilderBasicJwt() {
        String id = UUID.randomUUID().toString();
        String audience = "id_of_audience";

        Jwt jwt = Jwt.builder()
                .jwtId(id)
                .algorithm(JwkRSA.ALG_RS256)
                .addAudience(audience)
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
                .addAudience(audience)
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
        JwtValidator jwtValidator = JwtValidator.builder()
                .addDefaultTimeValidators()
                .addIssuerValidator(issuer, true)
                .addAudienceValidator(audience)
                .build();

        Errors errors = jwtValidator.validate(jwt);

        errors.log(LOGGER);
        errors.checkValid();

        //another try with defaults
        jwtValidator = JwtValidator.builder()
                .addDefaultTimeValidators()
                .addCriticalValidator()
                .addUserPrincipalValidator()
                .addIssuerValidator(issuer)
                .addAudienceValidator(audience)
                .build();

        errors = jwtValidator.validate(jwt);
        errors.log(LOGGER);
        errors.checkValid();
    }

    @Test
    public void testHelidonJsonApis() {
        Jwt jwt = Jwt.builder()
                .algorithm(JwkRSA.ALG_RS256)
                .headerBuilder(header -> header.addHeaderClaim("custom-header", "header-value"))
                .addPayloadClaim("custom-claim", "payload-value")
                .build();

        JsonObject headerJson = jwt.headerJsonObject();
        JsonObject payloadJson = jwt.payloadJsonObject();

        assertThat(headerJson.stringValue("alg"), is(Optional.of(JwkRSA.ALG_RS256)));
        assertThat(headerJson.stringValue("custom-header"), is(Optional.of("header-value")));
        assertThat(payloadJson.stringValue("custom-claim"), is(Optional.of("payload-value")));
        assertThat(jwt.headerClaimValue("custom-header").map(it -> it.asString().value()),
                   is(Optional.of("header-value")));
        assertThat(jwt.payloadClaimValue("custom-claim").map(it -> it.asString().value()),
                   is(Optional.of("payload-value")));
        assertThat(jwt.headers().headerClaimsJson().get("custom-header").asString().value(), is("header-value"));
        assertThat(jwt.payloadClaimsJson().get("custom-claim").asString().value(), is("payload-value"));
        assertThrows(UnsupportedOperationException.class,
                     () -> jwt.headers().headerClaimsJson().put("another-header", JsonString.create("another-value")));
        assertThrows(UnsupportedOperationException.class,
                     () -> jwt.payloadClaimsJson().put("another-claim", JsonString.create("another-value")));

        Jwt nullClaimJwt = Jwt.builder()
                .addPayloadClaim("nullable", null)
                .build();

        assertThat(nullClaimJwt.payloadJsonObject().stringValue("nullable"), is(Optional.of("null")));
    }

    @Test
    public void testCollectionClaimsRemainStructured() {
        Map<String, Object> nestedObject = new LinkedHashMap<>();
        nestedObject.put("nested", Optional.of("value"));
        nestedObject.put("ignored", Optional.empty());

        List<Object> claimValues = new ArrayList<>();
        claimValues.add(nestedObject);
        claimValues.add(null);
        claimValues.add(List.of("one", "two"));
        claimValues.add(Optional.of("present"));
        claimValues.add(Optional.empty());
        claimValues.add(new int[] {1, 2});

        Jwt jwt = Jwt.builder()
                .addPayloadClaim("complex", claimValues)
                .build();

        JsonArray values = jwt.payloadJsonObject().arrayValue("complex").orElseThrow();

        assertThat(values.values().size(), is(5));
        assertThat(values.get(0).orElseThrow().asObject().stringValue("nested"), is(Optional.of("value")));
        assertThat(values.get(0).orElseThrow().asObject().value("ignored"), is(Optional.empty()));
        assertThat(values.get(1).orElseThrow(), is(JsonNull.instance()));
        assertThat(values.get(2).orElseThrow().asArray(), is(JsonArray.createStrings(List.of("one", "two"))));
        assertThat(values.get(3).orElseThrow().asString().value(), is("present"));
        assertThat(values.get(4).orElseThrow().asArray().get(0).orElseThrow().asNumber().intValue(), is(1));
        assertThat(values.get(4).orElseThrow().asArray().get(1).orElseThrow().asNumber().intValue(), is(2));
    }

    @Test
    public void testOptionalStringClaimsIgnoreNonStringValues() {
        Jwt jwt = new Jwt(JwtHeaders.builder().build(),
                          JsonObject.builder()
                                  .set(Jwt.EMAIL, 42)
                                  .setNull(Jwt.NICKNAME)
                                  .build());

        assertThat(jwt.email(), is(Optional.empty()));
        assertThat(jwt.nickname(), is(Optional.empty()));
        assertThat(jwt.payloadClaimValue(Jwt.EMAIL).map(it -> it.asNumber().intValue()), is(Optional.of(42)));
    }
}
