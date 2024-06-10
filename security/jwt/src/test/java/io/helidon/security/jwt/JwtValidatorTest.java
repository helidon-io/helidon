/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

import io.helidon.common.Errors;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class JwtValidatorTest {

    private static final String HEADER_CLAIM = "myTest";
    private static final String HEADER_CLAIM_VALUE = "someValue";

    @Test
    public void testCriticalValidation() {
        Jwt jwt = Jwt.builder()
                .headerBuilder(builder -> builder.addHeaderCritical(HEADER_CLAIM)
                        .addHeaderClaim(HEADER_CLAIM, HEADER_CLAIM_VALUE))
                .build();

        JwtValidator jwtValidator = JwtValidator.builder()
                .addCriticalValidator()
                .addHeaderFieldValidator(HEADER_CLAIM, "My test field", HEADER_CLAIM_VALUE)
                .build();

        Errors result = jwtValidator.validate(jwt);
        result.checkValid();
    }

    @Test
    public void testCriticalRequiredHeaderMissing() {
        Jwt jwt = Jwt.builder()
                .headerBuilder(builder -> builder.addHeaderCritical(HEADER_CLAIM))
                .build();

        JwtValidator jwtValidator = JwtValidator.builder()
                .addCriticalValidator()
                .build();

        Errors result = jwtValidator.validate(jwt);
        assertThat(result.size(), is(1));
        assertThat(result.getFirst().getMessage(), is("JWT must contain [myTest], yet it contains: [crit]"));
    }

    @Test
    public void testCriticalDuplicate() {
        Jwt jwt = Jwt.builder()
                .headerBuilder(builder -> builder.addHeaderCritical(HEADER_CLAIM)
                        .addHeaderCritical(HEADER_CLAIM)
                        .addHeaderClaim(HEADER_CLAIM, HEADER_CLAIM_VALUE))
                .build();

        JwtValidator jwtValidator = JwtValidator.builder()
                .addCriticalValidator()
                .build();

        Errors result = jwtValidator.validate(jwt);
        assertThat(result.size(), is(1));
        assertThat(result.getFirst().getMessage(), is("JWT critical header contains duplicated values: [myTest, myTest]"));
    }

    @Test
    public void testCriticalHeaderNotProcessed() {
        Jwt jwt = Jwt.builder()
                .headerBuilder(builder -> builder.addHeaderCritical(HEADER_CLAIM)
                        .addHeaderClaim(HEADER_CLAIM, HEADER_CLAIM_VALUE))
                .build();

        JwtValidator jwtValidator = JwtValidator.builder()
                .addCriticalValidator()
                .build();

        Errors result = jwtValidator.validate(jwt);
        assertThat(result.size(), is(1));
        assertThat(result.getFirst().getMessage(), is("JWT is required to process [myTest], yet it process only [crit]"));
    }

    @Test
    public void testCriticalReservedRfcHeader() {
        Jwt jwt = Jwt.builder()
                .headerBuilder(builder -> builder.addHeaderCritical(JwtHeaders.ALGORITHM)
                        .addHeaderClaim(JwtHeaders.ALGORITHM, "123"))
                .build();

        JwtValidator jwtValidator = JwtValidator.builder()
                .addCriticalValidator()
                .build();

        Errors result = jwtValidator.validate(jwt);
        assertThat(result.size(), is(1));
        assertThat(result.getFirst().getMessage(), is("Required critical header value 'alg' is invalid. "
                                                              + "This required header is defined among JWA, JWE or JWS headers."));
    }

}
