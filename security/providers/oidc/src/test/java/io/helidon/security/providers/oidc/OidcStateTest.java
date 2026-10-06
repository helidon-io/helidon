/*
 * Copyright (c) 2026 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.security.providers.oidc;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.StringReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.security.Security;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;

import io.helidon.security.jwt.jwk.JwkKeys;
import io.helidon.security.providers.oidc.common.OidcConfig;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class OidcStateTest {
    private static final int POLICY_NOT_APPLIED = 77;

    private final OidcConfig config = configBuilder().build();

    @TempDir
    Path directory;

    // A separate JVM keeps crypto-policy changes out of the test runner and other tests.
    public static void main(String[] args) throws Exception {
        boolean restricted = Cipher.getMaxAllowedKeyLength("AES") < 256;
        if (restricted != "limited".equals(args[0])) {
            // Older JDKs can require installed policy files instead of supporting this override.
            System.exit(POLICY_NOT_APPLIED);
        }
        String policy = Security.getProperty("crypto.policy");
        for (boolean redirect : Arrays.asList(true, false)) {
            for (boolean query : Arrays.asList(false, true)) {
                for (boolean legacy : Arrays.asList(false, true)) {
                    OidcConfig config = configBuilder().redirect(redirect).useParam(query)
                            .legacyStateParam(legacy).legacyStateFallback(legacy).legacyQueryParamHandoff(legacy).build();
                    try {
                        assertAll(() -> assertCreation(restricted && (redirect || query), () -> OidcProvider.create(config)),
                                  () -> assertCreation(restricted, () -> OidcSupport.create(config)));
                    } finally {
                        config.appClient().close();
                        config.generalClient().close();
                    }
                }
            }
        }
        assertThat(Security.getProperty("crypto.policy"), is(policy));
    }

    // Reference writer for the source SymmetricCipher wire format, independent of the production adapter.
    static String encrypt(JsonObject json, String secret) throws Exception {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        PBEKeySpec keySpec = new PBEKeySpec(secret.toCharArray(), salt, 600_000, 256);
        try {
            byte[] key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(keySpec).getEncoded();
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"));
            byte[] iv = cipher.getIV();
            try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                 DataOutputStream output = new DataOutputStream(bytes)) {
                output.write(salt);
                output.writeInt(iv.length);
                output.write(iv);
                output.write(cipher.doFinal(json.toString().getBytes(StandardCharsets.UTF_8)));
                return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes.toByteArray());
            }
        } finally {
            keySpec.clearPassword();
        }
    }

    @AfterEach
    void closeClients() {
        config.appClient().close();
        config.generalClient().close();
    }

    @Test
    void limitedCryptoRejectsRedirectComponentsAtStartup() throws Exception {
        verifyCryptoPolicy("limited");
    }

    @Test
    void unlimitedCryptoAllowsRedirectComponents() throws Exception {
        verifyCryptoPolicy("unlimited");
    }

    @Test
    void queryResultUsesSourceCipherFormatAndLifetime() throws Exception {
        String result = OidcState.createQueryResult("access-token", Json.createObjectBuilder().build(), config);
        assertThat(result, startsWith("h."));
        JsonObject payload = decrypt(result.substring(2), config.clientSecret());
        assertThat(payload.getString("a"), is("access-token"));
        assertThat(payload.getJsonNumber("e").longValue() - payload.getJsonNumber("i").longValue(), is(60L));
        assertThat(payload.containsKey("n"), is(false));
        assertThat(OidcState.queryResultAccessToken(result, config, Optional.empty(), false),
                   is(Optional.of("access-token")));
    }

    @Test
    void queryResultRespectsShorterTokenLifetime() throws Exception {
        String result = OidcState.createQueryResult("access-token", Json.createObjectBuilder().add("expires_in", 5).build(),
                                                  config, "browser-nonce");
        JsonObject payload = decrypt(result.substring(2), config.clientSecret());
        assertThat(payload.getJsonNumber("e").longValue() - payload.getJsonNumber("i").longValue(), is(5L));
        assertThat(payload.getString("n"), is("browser-nonce"));
    }

    @Test
    void queryResultRequiresMatchingNonceWhenBound() {
        String result = OidcState.createQueryResult("access-token", Json.createObjectBuilder().build(), config, "nonce");
        assertThat(OidcState.queryResultAccessToken(result, config, Optional.of("nonce"), true),
                   is(Optional.of("access-token")));
        assertThat(OidcState.queryResultAccessToken(result, config, Optional.empty(), true), is(Optional.empty()));
        assertThat(OidcState.queryResultAccessToken(result, config, Optional.of("other"), true), is(Optional.empty()));
    }

    @Test
    void encryptedResultsUseFreshRandomness() {
        JsonObject response = Json.createObjectBuilder().build();
        assertThat(OidcState.createQueryResult("access-token", response, config),
                   not(OidcState.createQueryResult("access-token", response, config)));
    }

    @Test
    void acceptsSourceFormatAndRejectsExpiredOrFutureQueryResult() throws Exception {
        long now = Instant.now().getEpochSecond();
        assertThat(readQueryResult(queryPayload(now, now + 60)), is(Optional.of("access-token")));
        assertThat(readQueryResult(queryPayload(now - 120, now - 60)), is(Optional.empty()));
        assertThat(readQueryResult(queryPayload(now + 120, now + 180)), is(Optional.empty()));
    }

    @Test
    void rejectsTamperedQueryResult() throws Exception {
        String result = OidcState.createQueryResult("access-token", Json.createObjectBuilder().build(), config);
        byte[] bytes = Base64.getUrlDecoder().decode(result.substring(2));
        bytes[bytes.length - 1] ^= 1;
        String tampered = "h." + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        assertThat(OidcState.queryResultAccessToken(tampered, config, Optional.empty(), false), is(Optional.empty()));
        String wrongKey = "h." + encrypt(queryPayload(Instant.now().getEpochSecond(),
                                                      Instant.now().getEpochSecond() + 60), "other-secret");
        assertThat(OidcState.queryResultAccessToken(wrongKey, config, Optional.empty(), false), is(Optional.empty()));
    }

    @Test
    void rejectsInvalidAndOversizedQueryResults() {
        for (String invalid : Arrays.asList("", "plain-token", "h.", "h.invalid", "h." + repeat('x', 17 * 1024))) {
            assertThat(OidcState.queryResultAccessToken(invalid, config, Optional.empty(), false), is(Optional.empty()));
        }
        assertThrows(IllegalStateException.class,
                     () -> OidcState.createQueryResult(repeat('x', 17 * 1024), Json.createObjectBuilder().build(), config));
    }

    @Test
    void loginStateIsEncryptedBoundAndTimeLimited() throws Exception {
        String state = OidcState.createLoginState("/home?view=all", config, "nonce");
        JsonObject payload = decrypt(state, config.clientSecret());
        assertThat(payload.getString("r"), is("/home?view=all"));
        assertThat(payload.containsKey("a"), is(false));
        assertThat(payload.getJsonNumber("e").longValue() - payload.getJsonNumber("i").longValue(), is(300L));
        assertThat(OidcState.loginRedirect(state, config, Optional.of("nonce"), true), is(Optional.of("/home?view=all")));
        assertThat(OidcState.loginRedirect(state, config, Optional.empty(), true), is(Optional.empty()));
    }

    @Test
    void rejectsNonLocalRedirectsInsideAuthenticatedState() {
        for (String uri : Arrays.asList("https://attacker.example/", "//attacker.example/", "/\\attacker.example/",
                                       "/%2fattacker.example/", "/%5cattacker.example/", "/home\r\nInjected: value")) {
            String state = OidcState.createLoginState(uri, config, "nonce");
            assertThat(OidcState.loginRedirect(state, config, Optional.of("nonce"), true), is(Optional.empty()));
        }
    }

    @Test
    void acceptsOlderUnboundStateOnlyWhenBindingIsNotRequired() throws Exception {
        long now = Instant.now().getEpochSecond();
        String state = encrypt(Json.createObjectBuilder().add("r", "/home").add("i", now).add("e", now + 300).build(),
                               config.clientSecret());
        assertThat(OidcState.loginRedirect(state, config, Optional.empty(), true), is(Optional.empty()));
        assertThat(OidcState.loginRedirect(state, config, Optional.empty(), false), is(Optional.of("/home")));
    }

    private static OidcConfig.Builder configBuilder() {
        return OidcConfig.builder()
                .clientId("test-client")
                .clientSecret("test-secret")
                .identityUri(URI.create("https://issuer.example"))
                .tokenEndpointUri(URI.create("https://issuer.example/token"))
                .authorizationEndpointUri(URI.create("https://issuer.example/authorize"))
                .signJwk(JwkKeys.builder().build())
                .oidcMetadataWellKnown(false);
    }

    private static void assertCreation(boolean rejected, Supplier<?> factory) {
        if (rejected) {
            IllegalStateException failure = assertThrows(IllegalStateException.class, factory::get);
            assertThat(failure.getMessage(), allOf(containsString("AES-256"), containsString("unlimited"),
                                                  not(containsString("test-secret"))));
        } else {
            assertThat(factory.get(), notNullValue());
        }
    }

    private static JsonObject queryPayload(long issued, long expires) {
        return Json.createObjectBuilder().add("a", "access-token").add("i", issued).add("e", expires).build();
    }

    private static String repeat(char value, int count) {
        char[] chars = new char[count];
        Arrays.fill(chars, value);
        return new String(chars);
    }

    private static JsonObject decrypt(String value, String secret) throws Exception {
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(Base64.getUrlDecoder().decode(value)))) {
            byte[] salt = new byte[16];
            input.readFully(salt);
            byte[] iv = new byte[input.readInt()];
            input.readFully(iv);
            byte[] encrypted = new byte[input.available()];
            input.readFully(encrypted);
            PBEKeySpec keySpec = new PBEKeySpec(secret.toCharArray(), salt, 600_000, 256);
            try {
                byte[] key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(keySpec).getEncoded();
                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
                try (JsonReader reader = Json.createReader(new StringReader(
                        new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8)))) {
                    return reader.readObject();
                }
            } finally {
                keySpec.clearPassword();
            }
        }
    }

    private void verifyCryptoPolicy(String policy) throws Exception {
        Path properties = directory.resolve("crypto.security");
        Files.write(properties, ("crypto.policy=" + policy).getBytes(StandardCharsets.UTF_8));
        Path output = directory.resolve("crypto.log");
        String java = Paths.get(System.getProperty("java.home"), "bin", "java").toString();
        String classPath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        Process process = new ProcessBuilder(java, "-Djava.security.properties=" + properties.toUri(),
                                             "-cp", classPath, OidcStateTest.class.getName(), policy)
                .redirectErrorStream(true)
                .redirectOutput(output.toFile())
                .start();
        try {
            assertThat("Crypto policy subprocess timed out", process.waitFor(30, TimeUnit.SECONDS), is(true));
            String diagnostic = new String(Files.readAllBytes(output), StandardCharsets.UTF_8);
            assumeTrue(process.exitValue() != POLICY_NOT_APPLIED,
                       "The JVM does not support the requested per-process crypto policy");
            assertThat(diagnostic, process.exitValue(), is(0));
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly().waitFor(10, TimeUnit.SECONDS);
            }
        }
    }

    private Optional<String> readQueryResult(JsonObject payload) throws Exception {
        return OidcState.queryResultAccessToken("h." + encrypt(payload, config.clientSecret()), config, Optional.empty(), false);
    }
}
