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

import java.util.logging.Logger;

import io.helidon.common.Errors;
import io.helidon.common.configurable.Resource;
import io.helidon.security.jwt.jwk.Jwk;
import io.helidon.security.jwt.jwk.JwkKeys;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit test for {@link SignedJwt}.
 */
public class SignedJwtTest {
    private static final Logger LOGGER = Logger.getLogger(SignedJwtTest.class.getName());

    private static final String AUTH_0_TOKEN =
            "eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiIsImtpZCI6IlF6QkNNRE0xUVRJMk1qUkZNVEZETkRCRFJUWXdSa1U0UkRkRU16VTVSVGN3TkRSQk5qaENOUSJ9.eyJpc3MiOiJodHRwczovL2xhbmdvc2guZXUuYXV0aDAuY29tLyIsInN1YiI6ImF1dGgwfDU5NGEzNzJkNDUxN2FmMTA0ZjFiZGUzMCIsImF1ZCI6InliZlBpcnBwUTZaT094U0dLa2pnTWUxa2ZGTGZkbXlQIiwiZXhwIjoxNDk4MDgzOTI2LCJpYXQiOjE0OTgwNDc5MjZ9.nyMs5VfDV7Njd6hnQmbrSp_xVbIdvdP3ChzEtdffH2FWMqeW34gZT7dKJfgirdcBfXD4cDNDF2yjKZTe9-yCLWCFYtrfpvS_nlbt1hVM5ZR2HsGFSKdws0gOTsKCOTnD0SmfiQHCP-tzu87qWcVIwQcm-7AuLSfQ3WPxHAGPcQDOZiJBfcpBN4OGPKF0qq7PdNzBDDHmzpt2TbSsHmnSW-QbWZ1QHr52jsRCl1O_UTYHo2HE3ShE3WWBgYcJdJhgXNkhvJJh95oqmq_bfH5Saw-REmg-roU1bAh_yzFkmVSnhKmzHff432glRxcgDgF87kqJNodMD6UN6wRVt9vAPg";

    private static final String WRONG_TOKEN =
            "yJ4NXQjUzI1NiI6IlZjeXl1TVdxSGp4UjRVNmYzOTV3YmhUZXNZRmFaWXFSbDdBbUxjZE5sNXciLCJ4NXQiOiJTdEZFTlFaM2NMNndQaHFxODZnVmJTTG54TkUiLCJraWQiOiJTSUdOSU5HX0tFWSIsImFsZyI6IlJTMjU2In0.eyJzdWIiOiJIU01BcHAtY2xpZW50X0FQUElEIiwidXNlci50ZW5hbnQubmFtZSI6ImlkY3MtNzNmYTNlZDY5ZTgxNDFhN2I5MDFmYWY3Zjg3M2U3OGUiLCJzdWJfbWFwcGluZ2F0dHIiOiJ1c2VyTmFtZSIsImlzcyI6Imh0dHBzOlwvXC9pZGVudGl0eS5vcmFjbGVjbG91ZC5jb21cLyIsInRva190eXBlIjoiQVQiLCJjbGllbnRfaWQiOiJIU01BcHAtY2xpZW50X0FQUElEIiwiYXVkIjoiaHR0cDpcL1wvc2NhMDBjangudXMub3JhY2xlLmNvbTo3Nzc3Iiwic3ViX3R5cGUiOiJjbGllbnQiLCJzY29wZSI6InVybjpvcGM6cmVzb3VyY2U6Y29uc3VtZXI6OmFsbCIsImNsaWVudF90ZW5hbnRuYW1lIjoiaWRjcy03M2ZhM2VkNjllODE0MWE3YjkwMWZhZjdmODczZTc4ZSIsImV4cCI6MTU1MDU5NTk0MiwiaWF0IjoxNTUwNTA5NTQyLCJ0ZW5hbnRfaXNzIjoiaHR0cHM6XC9cL2lkY3MtNzNmYTNlZDY5ZTgxNDFhN2I5MDFmYWY3Zjg3M2U3OGUuaWRlbnRpdHkuYzlkZXYxLm9jOXFhZGV2LmNvbSIsImNsaWVudF9ndWlkIjoiN2JmZDM3MjM1ZGY3NDVjNDg5ZjYxZDM1ZTYzZGQ4ZmUiLCJjbGllbnRfbmFtZSI6IkhTTUFwcC1jbGllbnQiLCJ0ZW5hbnQiOiJpZGNzLTczZmEzZWQ2OWU4MTQxYTdiOTAxZmFmN2Y4NzNlNzhlIiwianRpIjoiYzRkNjlhZjUtOGQ4OC00N2Q2LTkzMDctN2RjMmI3NWY4MDQyIn0.ZsngUzzso_sW6rMg3jB-lueiC2sknIDRlgvjumMjp5rRSdLux2X4XZIm2Oa15JbcrnC6I4sgqB0xU1Wte-TW4hbBDLFhaJKYKiNaHBE0L7J73ZK7ITg7dORKkyjLrofGt0m8Rse1OlE9AWevz-l27gtQMO_mctGfHri2BxiMbSN1HwOjWW3kGoqPgCJZJfh2TiFlocEpsXDH4qB1qwhuIoT91gw3kIJlQov0_a9uGEepMU_RWMRjVZCIvuV2hPq_mdeWy2IhkHPxq422CLZ9MDOfbv8F6dY6DralCH4mmKbGM3dbqpZokWQxXG7LG9vWX1PFWw0N9clYHJ4QqBJ4pA";

    private static JwkKeys auth0Keys;
    private static JwkKeys customKeys;

    @BeforeAll
    public static void init() {
        auth0Keys = JwkKeys.builder()
                .resource(Resource.create("auth0-jwk.json"))
                .build();

        customKeys = JwkKeys.builder()
                .resource(Resource.create("jwk_data.json"))
                .build();
    }

    @Test
    public void testParsing() {
        SignedJwt signedJwt = SignedJwt.parseToken(AUTH_0_TOKEN);

        assertThat(signedJwt.headerJson(), notNullValue());
        assertThat(signedJwt.payloadJson(), notNullValue());
        assertThat(signedJwt.getSignature(), notNullValue());
        assertThat(signedJwt.getSignedBytes(), notNullValue());
        assertThat(signedJwt.tokenContent(), is(AUTH_0_TOKEN));
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

        Jwk defaultJwk = Jwk.NONE_JWK;

        SignedJwt signed = SignedJwt.sign(jwt, customKeys);
        assertThat(signed, notNullValue());
        assertThat(signed.getSignature(), notNullValue());
        assertThat(signed.getSignature(), is(new byte[0]));
        Errors errors = signed.verifySignature(customKeys, defaultJwk);
        assertThat(errors, notNullValue());
        errors.log(LOGGER);
        errors.checkValid();
    }

    @Test
    public void testSingatureNoAlgNoKid() {
        Jwt jwt = Jwt.builder().build();
        Jwk defaultJwk = Jwk.NONE_JWK;

        SignedJwt signed = SignedJwt.sign(jwt, customKeys);
        assertThat(signed, notNullValue());
        assertThat(signed.getSignature(), notNullValue());
        assertThat(signed.getSignature(), is(new byte[0]));
        Errors errors = signed.verifySignature(customKeys, defaultJwk);
        assertThat(errors, notNullValue());
        errors.log(LOGGER);
        errors.checkValid();
    }

    @Test
    public void testWrongToken() {
        assertThrows(Errors.ErrorMessagesException.class, () -> SignedJwt.parseToken(WRONG_TOKEN));
    }
}
