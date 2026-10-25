/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.common.pki;

import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PEMDecoder;
import java.security.PEMEncoder;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.helidon.common.configurable.Resource;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PemKeysTest {
    private static final char[] PASSWORD = "pem-test-password".toCharArray();
    private static final Map<String, KeyPair> KEY_PAIRS = new HashMap<>();

    private static X509Certificate firstCertificate;
    private static X509Certificate secondCertificate;

    @BeforeAll
    static void init() throws Exception {
        for (String algorithm : List.of("RSA", "DSA", "EC")) {
            var generator = KeyPairGenerator.getInstance(algorithm);
            generator.initialize(algorithm.equals("EC") ? 256 : 2048);
            KEY_PAIRS.put(algorithm, generator.generateKeyPair());
        }
        try (InputStream input = PemKeysTest.class.getResourceAsStream("/keystore/public_key_cert.pem")) {
            firstCertificate = PEMDecoder.of().decode(input, X509Certificate.class);
        }
        var keystore = KeyStore.getInstance("PKCS12");
        try (InputStream input = PemKeysTest.class.getResourceAsStream("/keystore/keystore-chain.p12")) {
            keystore.load(input, "helidon".toCharArray());
        }
        secondCertificate = (X509Certificate) keystore.getCertificate("1");
        assertThat("Bundle fixtures must distinguish certificate order", secondCertificate, not(firstCertificate));
    }

    @ParameterizedTest
    @ValueSource(strings = {"RSA", "DSA", "EC"})
    void testPkcs8AndPublicKeys(String algorithm) {
        KeyPair pair = KEY_PAIRS.get(algorithm);
        Keys keys = Keys.builder()
                .pem(pem -> pem.key(Resource.create("private key", PEMEncoder.of().encode(pair.getPrivate())))
                        .publicKey(Resource.create("public key", PEMEncoder.of().encode(pair.getPublic()))))
                .build();

        assertThat(keys.privateKey().orElseThrow().getEncoded(), is(pair.getPrivate().getEncoded()));
        assertThat(keys.publicKey().orElseThrow().getEncoded(), is(pair.getPublic().getEncoded()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"RSA", "DSA", "EC"})
    void testEncryptedPkcs8(String algorithm) {
        KeyPair pair = KEY_PAIRS.get(algorithm);
        byte[] encrypted = PEMEncoder.of().withEncryption(PASSWORD).encode(pair.getPrivate());
        Keys keys = Keys.builder()
                .pem(pem -> pem.key(Resource.create("encrypted private key", encrypted)).keyPassphrase(PASSWORD))
                .build();

        assertThat(keys.privateKey().orElseThrow().getEncoded(), is(pair.getPrivate().getEncoded()));
    }

    @Test
    void testEncryptedKeyWithoutPassword() {
        byte[] encrypted = PEMEncoder.of().withEncryption(PASSWORD).encode(KEY_PAIRS.get("RSA").getPrivate());

        assertThrows(PkiException.class, () -> Keys.builder()
                .pem(pem -> pem.key(Resource.create("encrypted private key", encrypted)))
                .build());
    }

    @Test
    void testEncryptedKeyWithWrongPassword() {
        byte[] encrypted = PEMEncoder.of().withEncryption(PASSWORD).encode(KEY_PAIRS.get("RSA").getPrivate());

        assertThrows(PkiException.class, () -> Keys.builder()
                .pem(pem -> pem.key(Resource.create("encrypted private key", encrypted))
                        .keyPassphrase("wrong password".toCharArray()))
                .build());
    }

    @Test
    void testPasswordDoesNotPreventReadingPlaintextKey() {
        KeyPair pair = KEY_PAIRS.get("RSA");
        Keys keys = Keys.builder()
                .pem(pem -> pem.key(Resource.create("private key", PEMEncoder.of().encode(pair.getPrivate())))
                        .keyPassphrase(PASSWORD))
                .build();

        assertThat(keys.privateKey().orElseThrow().getEncoded(), is(pair.getPrivate().getEncoded()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"key", "encrypted-key", "public-key"})
    void testKeyBundleSelectsFirstMatchingKey(String kind) {
        KeyPair pair = KEY_PAIRS.get("RSA");
        boolean publicKey = kind.equals("public-key");
        var encoder = kind.equals("encrypted-key") ? PEMEncoder.of().withEncryption(PASSWORD) : PEMEncoder.of();
        String bundle = "Leading text\n"
                + PEMEncoder.of().encodeToString(firstCertificate)
                + "-----BEGIN UNRECOGNIZED-----\nnot base64\n-----END UNRECOGNIZED-----\n"
                + (publicKey ? PEMEncoder.of().withEncryption(PASSWORD).encodeToString(pair.getPrivate())
                        : PEMEncoder.of().encodeToString(pair.getPublic()))
                + encoder.encodeToString(publicKey ? pair.getPublic() : pair.getPrivate())
                + encoder.encodeToString(publicKey ? KEY_PAIRS.get("EC").getPublic() : KEY_PAIRS.get("EC").getPrivate());
        Keys keys = Keys.builder()
                .pem(pem -> {
                    if (publicKey) {
                        pem.publicKey(Resource.create("public key bundle", bundle));
                    } else {
                        pem.key(Resource.create("private key bundle", bundle));
                        if (kind.equals("encrypted-key")) {
                            pem.keyPassphrase(PASSWORD);
                        }
                    }
                })
                .build();

        if (publicKey) {
            assertThat(keys.publicKey().orElseThrow().getEncoded(), is(pair.getPublic().getEncoded()));
        } else {
            assertThat(keys.privateKey().orElseThrow().getEncoded(), is(pair.getPrivate().getEncoded()));
        }
    }

    @Test
    void testCertificateBundleOrderDuplicatesAndUnrelatedObjects() {
        String bundle = "Leading text\n"
                + PEMEncoder.of().withEncryption(PASSWORD).encodeToString(KEY_PAIRS.get("RSA").getPrivate())
                + PEMEncoder.of().encodeToString(firstCertificate)
                + "-----BEGIN UNRECOGNIZED-----\nnot base64\n-----END UNRECOGNIZED-----\n"
                + PEMEncoder.of().encodeToString(secondCertificate)
                + PEMEncoder.of().encodeToString(firstCertificate)
                + "Trailing text\n \t\r\n";
        Keys keys = Keys.builder()
                .pem(pem -> pem.certChain(Resource.create("certificate chain", bundle))
                        .certificates(Resource.create("trust certificates", bundle)))
                .build();

        var expected = List.of(firstCertificate, secondCertificate, firstCertificate);
        assertThat(keys.certChain(), is(expected));
        assertThat(keys.certs(), is(expected));
        assertThat(keys.publicCert().orElseThrow(), is(firstCertificate));
        assertThat(keys.publicKey().orElseThrow(), is(firstCertificate.getPublicKey()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"X509 CERTIFICATE", "X.509 CERTIFICATE"})
    void testHistoricalCertificateLabels(String label) {
        String content = PEMEncoder.of().encodeToString(firstCertificate)
                .replace("CERTIFICATE-----", label + "-----");
        Keys keys = Keys.builder()
                .pem(pem -> pem.certChain(Resource.create("historical certificate label", content)))
                .build();

        assertThat(keys.certChain(), is(List.of(firstCertificate)));
    }

    @Test
    void testTrailingTextWithHyphensIsNotATruncatedPemObject() {
        String content = PEMEncoder.of().encodeToString(firstCertificate)
                + "Trailing text including -----Backup";
        Keys keys = Keys.builder()
                .pem(pem -> pem.certChain(Resource.create("certificate with trailing text", content)))
                .build();

        assertThat(keys.certChain(), is(List.of(firstCertificate)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "not PEM", "-----BEGIN CERTIFICATE-----\n",
            "-----BEGIN CERTIFICATE-----\ninvalid\n-----END CERTIFICATE-----\n"})
    void testInvalidCertificateInput(String content) {
        assertThrows(PkiException.class, () -> Keys.builder()
                .pem(pem -> pem.certChain(Resource.create("invalid certificate", content)))
                .build());
    }

    @Test
    void testTruncatedTrailingCertificateIsRejected() {
        String content = PEMEncoder.of().encodeToString(firstCertificate)
                + "-----BEGIN CERTIFICATE-----\nMIIB";

        assertThrows(PkiException.class, () -> Keys.builder()
                .pem(pem -> pem.certChain(Resource.create("truncated certificate bundle", content)))
                .build());
    }

    @ParameterizedTest
    @ValueSource(strings = {"key", "public-key", "cert-chain"})
    void testStreamsAreClosedAfterSuccessfulDecoding(String kind) {
        byte[] content = switch (kind) {
            case "key" -> PEMEncoder.of().encode(KEY_PAIRS.get("RSA").getPrivate());
            case "public-key" -> PEMEncoder.of().encode(KEY_PAIRS.get("RSA").getPublic());
            default -> PEMEncoder.of().encode(firstCertificate);
        };
        var input = new TrackedInputStream(content, false, false);

        load(kind, input);

        assertThat("PEM resource stream must be closed", input.closed, is(true));
    }

    @ParameterizedTest
    @ValueSource(strings = {"key", "public-key", "cert-chain"})
    void testStreamsAreClosedAfterReadFailure(String kind) {
        var input = new TrackedInputStream(new byte[0], true, false);

        assertThrows(PkiException.class, () -> load(kind, input));

        assertThat("Failing PEM resource stream must be closed", input.closed, is(true));
    }

    @Test
    void testMalformedPrivateKeyClosesStream() {
        String content = "-----BEGIN PRIVATE KEY-----\ninvalid\n-----END PRIVATE KEY-----\n";
        var input = new TrackedInputStream(content.getBytes(StandardCharsets.US_ASCII), false, false);

        assertThrows(PkiException.class, () -> load("key", input));

        assertThat("Invalid PEM resource stream must be closed", input.closed, is(true));
    }

    @Test
    void testCloseFailureDoesNotDiscardCertificates() {
        var input = new TrackedInputStream(PEMEncoder.of().encode(firstCertificate), false, true);

        Keys keys = load("cert-chain", input);

        assertThat(keys.certChain(), is(List.of(firstCertificate)));
        assertThat(input.closed, is(true));
    }

    @Test
    @SuppressWarnings("removal")
    void testLegacyCertificateReader() {
        String bundle = PEMEncoder.of().encodeToString(secondCertificate)
                + PEMEncoder.of().encodeToString(firstCertificate);
        var input = new TrackedInputStream(bundle.getBytes(StandardCharsets.US_ASCII), false, false);

        assertThat(PemReader.readCertificates(input), is(List.of(secondCertificate, firstCertificate)));
        assertThat(input.closed, is(true));
    }

    private static Keys load(String kind, InputStream input) {
        return Keys.builder()
                .pem(pem -> {
                    var resource = Resource.create("tracked PEM stream", input);
                    switch (kind) {
                    case "key" -> pem.key(resource);
                    case "public-key" -> pem.publicKey(resource);
                    case "cert-chain" -> pem.certChain(resource);
                    default -> throw new IllegalArgumentException("Unknown PEM resource: " + kind);
                    }
                })
                .build();
    }

    private static final class TrackedInputStream extends FilterInputStream {
        private final boolean failRead;
        private final boolean failClose;
        private boolean closed;

        private TrackedInputStream(byte[] content, boolean failRead, boolean failClose) {
            super(new ByteArrayInputStream(content));
            this.failRead = failRead;
            this.failClose = failClose;
        }

        @Override
        public int read() throws IOException {
            if (failRead) {
                throw new IOException("Test read failure");
            }
            return super.read();
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            if (failRead) {
                throw new IOException("Test read failure");
            }
            return super.read(bytes, offset, length);
        }

        @Override
        public int available() {
            return 0;
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
            if (failClose) {
                throw new IOException("Test close failure");
            }
        }
    }
}
