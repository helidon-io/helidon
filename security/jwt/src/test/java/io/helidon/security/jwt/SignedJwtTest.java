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

package io.helidon.security.jwt;

import java.util.logging.Logger;

import io.helidon.common.Errors;
import io.helidon.common.configurable.Resource;
import io.helidon.security.jwt.jwk.JwkKeys;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Unit test for {@link SignedJwt}.
 */
public class SignedJwtTest {
    private static final Logger LOGGER = Logger.getLogger(SignedJwtTest.class.getName());

    private static final String AUTH_0_TOKEN =
            "eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiIsImtpZCI6IlF6QkNNRE0xUVRJMk1qUkZNVEZETkRCRFJUWXdSa1U0UkRkRU16VTVSVGN3TkRSQk5qaENOUSJ9.eyJpc3MiOiJodHRwczovL2xhbmdvc2guZXUuYXV0aDAuY29tLyIsInN1YiI6ImF1dGgwfDU5NGEzNzJkNDUxN2FmMTA0ZjFiZGUzMCIsImF1ZCI6InliZlBpcnBwUTZaT094U0dLa2pnTWUxa2ZGTGZkbXlQIiwiZXhwIjoxNDk4MDgzOTI2LCJpYXQiOjE0OTgwNDc5MjZ9.nyMs5VfDV7Njd6hnQmbrSp_xVbIdvdP3ChzEtdffH2FWMqeW34gZT7dKJfgirdcBfXD4cDNDF2yjKZTe9-yCLWCFYtrfpvS_nlbt1hVM5ZR2HsGFSKdws0gOTsKCOTnD0SmfiQHCP-tzu87qWcVIwQcm-7AuLSfQ3WPxHAGPcQDOZiJBfcpBN4OGPKF0qq7PdNzBDDHmzpt2TbSsHmnSW-QbWZ1QHr52jsRCl1O_UTYHo2HE3ShE3WWBgYcJdJhgXNkhvJJh95oqmq_bfH5Saw-REmg-roU1bAh_yzFkmVSnhKmzHff432glRxcgDgF87kqJNodMD6UN6wRVt9vAPg";

    private static JwkKeys auth0Keys;
    private static JwkKeys customKeys;

    @BeforeAll
    public static void init() {
        auth0Keys = JwkKeys.builder()
                .resource(Resource.from("auth0-jwk.json"))
                .build();

        customKeys = JwkKeys.builder()
                .resource(Resource.from("jwk_data.json"))
                .build();
    }

    @Test
    public void testParsing() {
        SignedJwt signedJwt = SignedJwt.parseToken(AUTH_0_TOKEN);

        assertThat(signedJwt.getHeaderJson(), notNullValue());
        assertThat(signedJwt.getPayloadJson(), notNullValue());
        assertThat(signedJwt.getSignature(), notNullValue());
        assertThat(signedJwt.getSignedBytes(), notNullValue());
        assertThat(signedJwt.getTokenContent(), is(AUTH_0_TOKEN));
    }

    @Test
    public void testVerify() {
        SignedJwt signedJwt = SignedJwt.parseToken(AUTH_0_TOKEN);

        Errors errors = signedJwt.verifySignature(auth0Keys);
        assertThat(errors, notNullValue());
        errors.checkValid();
    }

    @Test
    public void testToJwt() {
        SignedJwt signedJwt = SignedJwt.parseToken(AUTH_0_TOKEN);
        Jwt jwt = signedJwt.getJwt();
        assertThat(jwt, notNullValue());
        //todo make sure everything is valid except for exp time
    }

    @Test
    public void testSignature() {
        Jwt jwt = Jwt.builder()
                .algorithm("RS256")
                .keyId("cc34c0a0-bd5a-4a3c-a50d-a2a7db7643df")
                .issuer("unit-test")
                .build();

        SignedJwt signed = SignedJwt.sign(jwt, customKeys);
        assertThat(signed, notNullValue());
        assertThat(signed.getSignature(), notNullValue());
        assertThat(signed.getSignature(), not(new byte[0]));
        Errors errors = signed.verifySignature(customKeys);
        assertThat(errors, notNullValue());
        errors.checkValid();
    }

    @Test
    public void testSignatureNone() {
        Jwt jwt = Jwt.builder()
                .algorithm("none")
                .issuer("unit-test")
                .build();

        SignedJwt signed = SignedJwt.sign(jwt, customKeys);
        assertThat(signed, notNullValue());
        assertThat(signed.getSignature(), notNullValue());
        assertThat(signed.getSignature(), is(new byte[0]));
        Errors errors = signed.verifySignature(customKeys);
        assertThat(errors, notNullValue());
        errors.log(LOGGER);
        errors.checkValid();
    }

    @Test
    public void testSingatureNoAlgNoKid() {
        Jwt jwt = Jwt.builder().build();

        SignedJwt signed = SignedJwt.sign(jwt, customKeys);
        assertThat(signed, notNullValue());
        assertThat(signed.getSignature(), notNullValue());
        assertThat(signed.getSignature(), is(new byte[0]));
        Errors errors = signed.verifySignature(customKeys);
        assertThat(errors, notNullValue());
        errors.log(LOGGER);
        errors.checkValid();
    }
}
