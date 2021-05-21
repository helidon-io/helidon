/*
 * Copyright (c) 2018, 2019 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.security.providers.httpauth;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.common.Builder;
import io.helidon.security.AuthenticationResponse;
import io.helidon.security.Principal;
import io.helidon.security.Security;
import io.helidon.security.SecurityContext;
import io.helidon.security.SecurityResponse;
import io.helidon.security.Subject;
import io.helidon.security.spi.AuthenticationProvider;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Unit test for {@link HttpBasicAuthProvider} and {@link HttpDigestAuthProvider}.
 */
public class HttpAuthProviderBuilderTest {
    private static final AtomicInteger COUNTER = new AtomicInteger();
    private static final String QUOTE = "\"";

    private static Security security;

    private final Random random = new Random();
    private SecurityContext context;

    @BeforeAll
    public static void initClass() {
        SecureUserStore us = userStore();

        security = Security.builder()
                .addAuthenticationProvider(basicAuthProvider(false,us), "basic")
                .addAuthenticationProvider(basicAuthProvider(true, us), "basic_optional")
                .addAuthenticationProvider(digestAuthProvider(false, false, us), "digest")
                .addAuthenticationProvider(digestAuthProvider(false, true, us), "digest_optional")
                .addAuthenticationProvider(digestAuthProvider(true, false, us), "digest_old")
                .build();
    }

    private static SecureUserStore userStore() {
        return login -> {
            if ("jack".equals(login)) {
                return Optional.of(new TestUser("jack",
                                                "jackIsGreat".toCharArray(),
                                                Set.of("user", "admin")));
            }
            if ("jill".equals(login)) {
                return Optional.of(new TestUser("jill",
                                                "password".toCharArray(),
                                                Set.of("user")));
            }

            return Optional.empty();
        };
    }

    private static Builder<? extends AuthenticationProvider> basicAuthProvider(boolean optional, SecureUserStore us) {
        return HttpBasicAuthProvider.builder()
                .realm("mic")
                .optional(optional)
                .userStore(us);
    }

    private static Builder<? extends AuthenticationProvider> digestAuthProvider(boolean old, boolean optional, SecureUserStore us) {
        HttpDigestAuthProvider.Builder builder = HttpDigestAuthProvider.builder()
                .realm("mic")
                .optional(optional)
                .digestServerSecret("pwd".toCharArray())
                .userStore(us);

        if (old) {
            builder.noDigestQop();
        }

        return builder;
    }

    @BeforeEach
    public void init() {
        context = security.contextBuilder(String.valueOf(COUNTER.getAndIncrement()))
                .build();
    }

    @Test
    public void basicTestOptional() {
        AuthenticationResponse response = context.atnClientBuilder().explicitProvider("basic_optional").buildAndGet();

        assertThat(response.status().isSuccess(), is(false));
        assertThat(response.status().name(), is(SecurityResponse.SecurityStatus.ABSTAIN.name()));
        assertThat(response.statusCode().orElse(200), is(200));
        assertThat(response.description().orElse(""), is("No authorization header"));

        setHeader(context, HttpBasicAuthProvider.HEADER_AUTHENTICATION, buildBasic("jack", "invalid_passworrd"));
        System.out.println("test");
        response = context.atnClientBuilder().explicitProvider("basic_optional").buildAndGet();

        assertThat(response.status().isSuccess(), is(false));
        assertThat(response.status().name(), is(SecurityResponse.SecurityStatus.ABSTAIN.name()));
        assertThat(response.statusCode().orElse(200), is(200));
        assertThat(response.description().orElse(""), is("Invalid username or password"));
    }

    @Test
    public void basicTestFail() {
        AuthenticationResponse response = context.atnClientBuilder().buildAndGet();

        assertThat(response.status().isSuccess(), is(false));
        assertThat(response.statusCode().orElse(200), is(401));
        String authHeader = response.responseHeaders().get(HttpBasicAuthProvider.HEADER_AUTHENTICATION_REQUIRED).get(0);
        assertThat(authHeader, notNullValue());
        assertThat(authHeader.toLowerCase(), is("basic realm=\"mic\""));
    }

    @Test
    public void basicTestJack() {
        setHeader(context, HttpBasicAuthProvider.HEADER_AUTHENTICATION, buildBasic("jack", "jackIsGreat"));

        AuthenticationResponse response = context.authenticate();

        assertThat(response.status(), is(SecurityResponse.SecurityStatus.SUCCESS));
        assertThat(response.statusCode().orElse(200), is(200));

        assertThat(context.user().map(sub -> sub.principal().getName()).orElse(null), is("jack"));
        assertThat(context.isUserInRole("admin"), is(true));
        assertThat(context.isUserInRole("user"), is(true));
    }

    private void setHeader(SecurityContext context, String name, String value) {
        context.env(context.env()
                            .derive()
                            .header(name, value)
                            .build());
    }

    @Test
    public void basicTestInvalidUser() {
        setHeader(context, HttpBasicAuthProvider.HEADER_AUTHENTICATION, buildBasic("wrong", "user"));

        AuthenticationResponse response = context.authenticate();

        assertThat(response.description().orElse(""), is("Invalid username or password"));
        assertThat(response.status().isSuccess(), is(false));
        assertThat(response.statusCode().orElse(200), is(401));
        String authHeader = response.responseHeaders().get(HttpBasicAuthProvider.HEADER_AUTHENTICATION_REQUIRED).get(0);
        assertThat(authHeader, notNullValue());
        assertThat(authHeader.toLowerCase(), is("basic realm=\"mic\""));

        setHeader(context, HttpBasicAuthProvider.HEADER_AUTHENTICATION, buildBasic("jack", "invalid_passworrd"));

        response = context.authenticate();

        assertThat(response.description().orElse(""), is("Invalid username or password"));
        assertThat(response.status().isSuccess(), is(false));
        assertThat(response.statusCode().orElse(200), is(401));
    }

    @Test
    public void sendInvalidTypeTest() {
        setHeader(context, HttpBasicAuthProvider.HEADER_AUTHENTICATION, "bearer token=\"adfasfaf\"");

        AuthenticationResponse response = context.authenticate();

        assertThat(response.status(), is(SecurityResponse.SecurityStatus.FAILURE));
        assertThat(response.statusCode().orElse(200), is(401));
    }

    @Test
    public void sendInvalidBasicTest() {
        setHeader(context, HttpBasicAuthProvider.HEADER_AUTHENTICATION, "basic wrong_header_value");
        AuthenticationResponse response = context.authenticate();

        assertThat(response.status(), is(SecurityResponse.SecurityStatus.FAILURE));
        assertThat(response.statusCode().orElse(200), is(401));

        // not base64 encoded and invalid
        setHeader(context,
                  HttpBasicAuthProvider.HEADER_AUTHENTICATION,
                  "basic " + Base64.getEncoder().encodeToString("Hello".getBytes()));

        response = context.authenticate();

        assertThat(response.status(), is(SecurityResponse.SecurityStatus.FAILURE));
        assertThat(response.statusCode().orElse(200), is(401));
    }

    @Test
    public void sendDigestNotBasicTest() {
        setHeader(context, HttpBasicAuthProvider.HEADER_AUTHENTICATION, buildDigest(HttpDigest.Qop.AUTH, "jack", "jackIsGreat"));
        AuthenticationResponse response = context.authenticate();

        assertThat(response.status(), is(SecurityResponse.SecurityStatus.FAILURE));
        assertThat(response.statusCode().orElse(200), is(401));
    }

    @Test
    public void basicTestJill() {
        setHeader(context, HttpBasicAuthProvider.HEADER_AUTHENTICATION, buildBasic("jill", "password"));
        AuthenticationResponse response = context.authenticate();

        assertThat(response.status(), is(SecurityResponse.SecurityStatus.SUCCESS));
        assertThat(response.statusCode().orElse(200), is(200));

        assertThat(getUsername(context), is("jill"));
        assertThat(context.isUserInRole("admin"), is(false));
        assertThat(context.isUserInRole("user"), is(true));
    }

    @Test
    public void digestTestOptional() {
        AuthenticationResponse response = context.atnClientBuilder()
                .explicitProvider("digest_optional")
                .buildAndGet();

        assertThat(response.status().isSuccess(), is(false));
        assertThat(response.status().name(), is(SecurityResponse.SecurityStatus.ABSTAIN.name()));
        assertThat(response.statusCode().orElse(200), is(200));
        assertThat(response.description().orElse(""), is("No authorization header"));

        setHeader(context, HttpBasicAuthProvider.HEADER_AUTHENTICATION, buildDigest(HttpDigest.Qop.AUTH, "wrong", "user"));
        response = context.atnClientBuilder()
                .explicitProvider("digest_optional")
                .buildAndGet();

        assertThat(response.status().isSuccess(), is(false));
        assertThat(response.status().name(), is(SecurityResponse.SecurityStatus.ABSTAIN.name()));
        assertThat(response.statusCode().orElse(200), is(200));
        assertThat(response.description().orElse(""), is("Invalid username or password"));
    }

    @Test
    public void digestTest401() {
        AuthenticationResponse response = context.atnClientBuilder()
                .explicitProvider("digest")
                .buildAndGet();

        assertThat(response.status().isSuccess(), is(false));
        assertThat(response.statusCode().orElse(200), is(401));
        String authHeader = response.responseHeaders().get(HttpBasicAuthProvider.HEADER_AUTHENTICATION_REQUIRED).get(0);
        assertThat(authHeader, notNullValue());
        assertThat(authHeader.toLowerCase(), startsWith("digest realm=\"mic\""));
        assertThat(authHeader.toLowerCase(), containsString("qop="));
    }

    @Test
    public void digestOldTest401() {
        AuthenticationResponse response = context.atnClientBuilder()
                .explicitProvider("digest_old")
                .buildAndGet();

        assertThat(response.status().isSuccess(), is(false));
        assertThat(response.statusCode().orElse(200), is(401));
        String authHeader = response.responseHeaders().get(HttpBasicAuthProvider.HEADER_AUTHENTICATION_REQUIRED).get(0);
        assertThat(authHeader, notNullValue());
        assertThat(authHeader.toLowerCase(), startsWith("digest realm=\"mic\""));
        assertThat(authHeader.toLowerCase(), not(containsString("qop=")));
    }

    @Test
    public void digestTestJack() {
        setHeader(context, HttpBasicAuthProvider.HEADER_AUTHENTICATION, buildDigest(HttpDigest.Qop.AUTH, "jack", "jackIsGreat"));
        AuthenticationResponse response = context.atnClientBuilder()
                .explicitProvider("digest")
                .buildAndGet();

        assertThat(response.description().orElse("No description"),
                   response.status(),
                   is(SecurityResponse.SecurityStatus.SUCCESS));
        assertThat(response.statusCode().orElse(200), is(200));

        assertThat(getUsername(context), is("jack"));
        assertThat(context.isUserInRole("admin"), is(true));
        assertThat(context.isUserInRole("user"), is(true));
    }

    @Test
    public void digestTestInvalidUser() {
        setHeader(context, HttpBasicAuthProvider.HEADER_AUTHENTICATION, buildDigest(HttpDigest.Qop.AUTH, "wrong", "user"));
        AuthenticationResponse response = context.atnClientBuilder()
                .explicitProvider("digest")
                .buildAndGet();

        assertThat(response.description().orElse(""), is("Invalid username or password"));
        assertThat(response.status().isSuccess(), is(false));
        assertThat(response.statusCode().orElse(200), is(401));
        String authHeader = response.responseHeaders().get(HttpBasicAuthProvider.HEADER_AUTHENTICATION_REQUIRED).get(0);
        assertThat(authHeader, notNullValue());
        assertThat(authHeader.toLowerCase(), startsWith("digest realm=\"mic\""));

        setHeader(context,
                  HttpBasicAuthProvider.HEADER_AUTHENTICATION,
                  buildDigest(HttpDigest.Qop.AUTH, "jack", "wrong password"));
        response = context.atnClientBuilder()
                .explicitProvider("digest")
                .buildAndGet();

        assertThat(response.description().orElse(""), is("Invalid username or password"));
        assertThat(response.status().isSuccess(), is(false));
        assertThat(response.statusCode().orElse(200), is(401));
    }

    @Test
    public void sendBasicNotDigestTest() {
        setHeader(context, HttpBasicAuthProvider.HEADER_AUTHENTICATION, buildBasic("jack", "jackIsGreat"));
        AuthenticationResponse response = context.atnClientBuilder()
                .explicitProvider("digest")
                .buildAndGet();

        assertThat(response.status(), is(SecurityResponse.SecurityStatus.FAILURE));
        assertThat(response.statusCode().orElse(200), is(401));
    }

    @Test
    public void sendInvalidDigestTest() {
        setHeader(context, HttpBasicAuthProvider.HEADER_AUTHENTICATION, "digest wrong_header_value");
        AuthenticationResponse response = context.atnClientBuilder()
                .explicitProvider("digest")
                .buildAndGet();

        assertThat(response.status(), is(SecurityResponse.SecurityStatus.FAILURE));
        assertThat(response.statusCode().orElse(200), is(401));
    }

    @Test
    public void digestTestJill() {
        setHeader(context, HttpBasicAuthProvider.HEADER_AUTHENTICATION, buildDigest(HttpDigest.Qop.AUTH, "jill", "password"));
        AuthenticationResponse response = context.atnClientBuilder()
                .explicitProvider("digest")
                .buildAndGet();

        assertThat(response.description().orElse("No description"),
                   response.status(),
                   is(SecurityResponse.SecurityStatus.SUCCESS));
        assertThat(response.statusCode().orElse(200), is(200));

        assertThat(getUsername(context), is("jill"));
        assertThat(context.isUserInRole("admin"), is(false));
        assertThat(context.isUserInRole("user"), is(true));
    }

    private String getUsername(SecurityContext context) {
        return context.user().map(Subject::principal).map(Principal::getName).orElse(null);
    }

    @Test
    public void digestOldTestJack() {
        setHeader(context, HttpBasicAuthProvider.HEADER_AUTHENTICATION, buildDigest(HttpDigest.Qop.NONE, "jack", "jackIsGreat"));
        AuthenticationResponse response = context.atnClientBuilder()
                .explicitProvider("digest_old")
                .buildAndGet();

        assertThat(response.description().orElse("No description"),
                   response.status(),
                   is(SecurityResponse.SecurityStatus.SUCCESS));
        assertThat(response.statusCode().orElse(200), is(200));

        assertThat(getUsername(context), is("jack"));
        assertThat(context.isUserInRole("admin"), is(true));
        assertThat(context.isUserInRole("user"), is(true));
    }

    @Test
    public void digestOldTestJill() {
        setHeader(context, HttpBasicAuthProvider.HEADER_AUTHENTICATION, buildDigest(HttpDigest.Qop.NONE, "jill", "password"));
        AuthenticationResponse response = context.atnClientBuilder()
                .explicitProvider("digest_old")
                .buildAndGet();

        assertThat(response.description().orElse("No description"),
                   response.status(),
                   is(SecurityResponse.SecurityStatus.SUCCESS));
        assertThat(response.statusCode().orElse(200), is(200));

        assertThat(getUsername(context), is("jill"));
        assertThat(context.isUserInRole("admin"), is(false));
        assertThat(context.isUserInRole("user"), is(true));
    }

    @Test
    public void digestTestNonceTimeout() {
        Instant in = Instant.now().minus(100, ChronoUnit.DAYS);

        setHeader(context, HttpBasicAuthProvider.HEADER_AUTHENTICATION,
                  buildDigest(HttpDigest.Qop.AUTH,
                              "jack",
                              "jackIsGreat",
                              HttpDigestAuthProvider.nonce(in.toEpochMilli(), random, "pwd".toCharArray()),
                              "mic"));

        AuthenticationResponse response = context.atnClientBuilder()
                .explicitProvider("digest")
                .buildAndGet();

        assertThat(response.description().orElse(""), is("Nonce timeout"));
        assertThat(response.status(), is(SecurityResponse.SecurityStatus.FAILURE));
        assertThat(response.statusCode().orElse(200), is(401));
    }

    @Test
    public void digestTestNonceNotB64() {
        setHeader(context, HttpBasicAuthProvider.HEADER_AUTHENTICATION,
                  buildDigest(HttpDigest.Qop.AUTH,
                              "jack",
                              "jackIsGreat",
                              "Not a base64 encoded $tring",
                              "mic"));
        AuthenticationResponse response = context.atnClientBuilder()
                .explicitProvider("digest")
                .buildAndGet();

        assertThat(response.description().orElse(""), is("Nonce must be base64 encoded"));
        assertThat(response.status(), is(SecurityResponse.SecurityStatus.FAILURE));
        assertThat(response.statusCode().orElse(200), is(401));
    }

    @Test
    public void digestTestNonceTooShort() {
        setHeader(context, HttpBasicAuthProvider.HEADER_AUTHENTICATION,
                  buildDigest(HttpDigest.Qop.AUTH,
                              "jack",
                              "jackIsGreat",
                              // must be base64 encoded string of less than 17 bytes
                              "wrongNonce",
                              "mic"));

        AuthenticationResponse response = context.atnClientBuilder()
                .explicitProvider("digest")
                .buildAndGet();
        assertThat(response.description().orElse(""), is("Invalid nonce length"));
        assertThat(response.status(), is(SecurityResponse.SecurityStatus.FAILURE));
        assertThat(response.statusCode().orElse(200), is(401));
    }

    @Test
    public void digestTestNonceNotEncrypted() {
        setHeader(context, HttpBasicAuthProvider.HEADER_AUTHENTICATION,
                  buildDigest(HttpDigest.Qop.AUTH,
                              "jack",
                              "jackIsGreat",
                              Base64.getEncoder()
                                      .encodeToString("4444444444444444444444444444444444444444444444".getBytes()),
                              "mic"));

        AuthenticationResponse response = context.atnClientBuilder()
                .explicitProvider("digest")
                .buildAndGet();
        assertThat(response.description().orElse(""), is("Invalid nonce value"));
        assertThat(response.status(), is(SecurityResponse.SecurityStatus.FAILURE));
        assertThat(response.statusCode().orElse(200), is(401));
    }

    @Test
    public void digestTestWrongRealm() {
        setHeader(context, HttpBasicAuthProvider.HEADER_AUTHENTICATION,
                  buildDigest(HttpDigest.Qop.AUTH,
                              "jack",
                              "jackIsGreat",
                              HttpDigestAuthProvider.nonce(System.currentTimeMillis(), random, "pwd".toCharArray()),
                              "wrongRealm"));
        AuthenticationResponse response = context.atnClientBuilder()
                .explicitProvider("digest")
                .buildAndGet();

        assertThat(response.description().orElse(""), is("Invalid realm"));
        assertThat(response.status(), is(SecurityResponse.SecurityStatus.FAILURE));
        assertThat(response.statusCode().orElse(200), is(401));
    }

    private String buildBasic(String user, String password) {
        return "basic " + Base64.getEncoder()
                .encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8));
    }

    private String buildDigest(HttpDigest.Qop qop,
                               String user,
                               String password,
                               String nonce,
                               String realm) {

        StringBuilder result = new StringBuilder(100);

        String opaque = "someString";
        String path = "/digest";
        String cnonce = createCnonce();
        String nc = "00000001";

        DigestToken token = new DigestToken();
        token.setAlgorithm(HttpDigest.Algorithm.MD5);
        token.setQop(qop);
        token.setUsername(user);
        token.setOpaque(opaque);
        token.setRealm(realm);
        token.setMethod("GET");
        token.setUri(path);
        token.setNonce(nonce);
        token.setNc(nc);
        token.setCnonce(cnonce);

        String response = token.digest(password.toCharArray());

        result.append("digest ");
        result.append("username=").append(QUOTE).append(user).append(QUOTE);
        result.append(", realm=").append(QUOTE).append(realm).append(QUOTE);
        result.append(", nonce=").append(QUOTE).append(nonce).append(QUOTE);
        result.append(", uri=").append(QUOTE).append(path).append(QUOTE);
        result.append(", algorithm=MD5");
        result.append(", response=").append(QUOTE).append(response).append(QUOTE);
        result.append(", opaque=").append(QUOTE).append(opaque).append(QUOTE);
        if (qop != HttpDigest.Qop.NONE) {
            result.append(", qop=").append(qop.getQop());
        }
        result.append(", nc=").append(nc);
        result.append(", cnonce=").append(QUOTE).append(cnonce).append(QUOTE);

        return result.toString();
    }

    private String buildDigest(HttpDigest.Qop qop, String user, String password) {
        return buildDigest(qop,
                           user,
                           password,
                           HttpDigestAuthProvider.nonce(System.currentTimeMillis(), random, "pwd".toCharArray()),
                           "mic");
    }

    private String createCnonce() {
        byte[] cnonce = new byte[8];
        random.nextBytes(cnonce);

        return Base64.getEncoder().encodeToString(cnonce);
    }

}
