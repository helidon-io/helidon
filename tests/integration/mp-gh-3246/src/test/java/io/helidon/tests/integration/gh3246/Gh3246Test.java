/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

package io.helidon.tests.integration.gh3246;

import java.time.Instant;

import javax.inject.Inject;
import javax.ws.rs.client.WebTarget;

import io.helidon.common.configurable.Resource;
import io.helidon.common.http.Http;
import io.helidon.microprofile.tests.junit5.AddBean;
import io.helidon.microprofile.tests.junit5.HelidonTest;
import io.helidon.security.jwt.Jwt;
import io.helidon.security.jwt.SignedJwt;
import io.helidon.security.jwt.jwk.JwkKeys;
import io.helidon.security.jwt.jwk.JwkRSA;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@HelidonTest
@AddBean(Gh3246Resource.class)
public class Gh3246Test {
    @Inject
    private WebTarget webTarget;

    @Test
    void testUnsecuredCallout() {
        int port = webTarget.getUri().getPort();

        String response = webTarget.path("/test/callout")
                .queryParam("port", port)
                .request()
                .get(String.class);

        assertThat(response, is("hello"));
    }

    @Test
    void testUnsecuredDirect() {
        String response = webTarget.path("/test/hello")
                .request()
                .get(String.class);

        assertThat(response, is("hello"));
    }

    @Test
    void testSecuredCallout() {
        Jwt jwt = Jwt.builder()
                .subject("jack")
                .addUserGroup("admin")
                .algorithm(JwkRSA.ALG_RS256)
                .issuer("test-gh-3246")
                .audience("http://example.helidon.io")
                .issueTime(Instant.now())
                .userPrincipal("jack")
                .keyId("SIGNING_KEY")
                .build();

        JwkKeys jwkKeys = JwkKeys.builder()
                .resource(Resource.create("sign-jwk.json"))
                .build();

        SignedJwt signed = SignedJwt.sign(jwt, jwkKeys.forKeyId("sign-rsa").get());
        String tokenContent = signed.tokenContent();

        int port = webTarget.getUri().getPort();

        String response = webTarget.path("/test/secured")
                .queryParam("port", port)
                .request()
                .header(Http.Header.AUTHORIZATION, "Bearer " + tokenContent)
                .get(String.class);

        assertThat(response, is("hello"));
    }
}
