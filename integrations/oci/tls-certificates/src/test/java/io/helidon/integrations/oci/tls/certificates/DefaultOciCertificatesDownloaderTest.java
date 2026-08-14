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

package io.helidon.integrations.oci.tls.certificates;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.integrations.oci.tls.certificates.spi.OciCertificatesDownloader.CertificatesWithPrivateKey;

import com.oracle.bmc.Region;
import com.oracle.bmc.auth.AuthCachingPolicy;
import com.oracle.bmc.auth.BasicAuthenticationDetailsProvider;
import com.oracle.bmc.auth.ProvidesClientConfigurators;
import com.oracle.bmc.auth.RegionProvider;
import com.oracle.bmc.certificates.model.CertificateBundle;
import com.oracle.bmc.certificates.model.CertificateBundlePublicOnly;
import com.oracle.bmc.certificates.model.CertificateBundleWithPrivateKey;
import com.oracle.bmc.certificates.responses.GetCertificateBundleResponse;
import com.oracle.bmc.http.ClientConfigurator;
import com.oracle.bmc.http.Priorities;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DefaultOciCertificatesDownloaderTest {
    private static final String TEST_KEYS = "/test-keys/";
    private static final String PASSPHRASE = "changeit";

    @Test
    void requestsExplicitCurrentPrivateKeyBundle() {
        AtomicReference<URI> capturedUri = new AtomicReference<>();
        DefaultOciCertificatesDownloader downloader =
                new DefaultOciCertificatesDownloader(new RequestCapturingAuthProvider(capturedUri));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> downloader.loadCertificatesWithPrivateKey("certificate-ocid"));

        assertThat(exceptionText(exception), containsString(RequestCapturedException.MESSAGE));
        URI requestUri = capturedUri.get();
        assertThat(requestUri, notNullValue());
        assertThat(requestUri.getRawPath(), is("/20210224/certificateBundles/certificate-ocid"));
        assertThat(Arrays.asList(requestUri.getRawQuery().split("&")),
                   containsInAnyOrder("stage=CURRENT",
                                      "certificateBundleType=CERTIFICATE_CONTENT_WITH_PRIVATE_KEY"));
    }

    @Test
    void decodesUnencryptedRsaBundleAndPrefersVersionNumber() {
        CertificatesWithPrivateKey result = decode("serverCert.pem", "ca.pem", "serverKey.pem", null, 42L, "etag");

        assertThat(result.version(), is("42"));
        assertThat(result.certificates().length, is(2));
        assertThat(result.privateKey().getAlgorithm(), is("RSA"));
    }

    @Test
    void decodesEncryptedRsaBundle() {
        CertificatesWithPrivateKey result =
                decode("serverCert.pem", "", "serverKeyEncrypted.pem", PASSPHRASE, 1L, null);

        assertThat(result.certificates().length, is(1));
        assertThat(result.privateKey().getAlgorithm(), is("RSA"));
    }

    @Test
    void decodesUnencryptedEcBundle() {
        CertificatesWithPrivateKey result = decode("ecCert.pem", "", "ecKey.pem", null, 2L, null);

        assertThat(result.certificates().length, is(1));
        assertThat(result.privateKey().getAlgorithm(), is("EC"));
    }

    @Test
    void decodesEncryptedEcBundle() {
        CertificatesWithPrivateKey result = decode("ecCert.pem", "", "ecKeyEncrypted.pem", PASSPHRASE, 3L, null);

        assertThat(result.certificates().length, is(1));
        assertThat(result.privateKey().getAlgorithm(), is("EC"));
    }

    @Test
    void fallsBackFromVersionNumberToEtagAndCertificateHash() {
        CertificatesWithPrivateKey result = decode("serverCert.pem", "", "serverKey.pem", null, null, "etag");
        assertThat(result.version(), is("etag"));

        X509Certificate certificate = result.certificates()[0];
        assertThat(DefaultOciCertificatesDownloader.toVersion(null, null, new Certificate[] {certificate}),
                   is(String.valueOf(Arrays.hashCode(new Certificate[] {certificate}))));
    }

    @Test
    void rejectsPublicOnlyBundle() {
        GetCertificateBundleResponse response = response(
                CertificateBundlePublicOnly.builder()
                        .certificatePem(resource("serverCert.pem"))
                        .certChainPem("")
                        .build(),
                "etag");

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> DefaultOciCertificatesDownloader.toCertificatesWithPrivateKey(response));

        assertThat(exception.getMessage(), containsString("does not contain a private key"));
    }

    @Test
    void rejectsMismatchedPrivateKey() {
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> decode("serverCert.pem", "", "ecKey.pem", null, 1L, null));

        assertThat(exception.getMessage(), containsString("does not match the leaf certificate"));
    }

    @Test
    void rejectsWrongPassphraseWithoutLeakingBundleMaterial() {
        String secretPassphrase = "secret-passphrase-must-not-leak";
        CertificateBundleWithPrivateKey bundle = CertificateBundleWithPrivateKey.builder()
                .versionNumber(1L)
                .certificatePem(resource("serverCert.pem"))
                .certChainPem("")
                .privateKeyPem(resource("serverKeyEncrypted.pem"))
                .privateKeyPemPassphrase(secretPassphrase)
                .build();

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> DefaultOciCertificatesDownloader.toCertificatesWithPrivateKey(response(bundle, null)));

        assertThat(exceptionText(exception), containsString("private key cannot be decoded"));
        assertThat(exceptionText(exception), not(containsString(secretPassphrase)));
        assertThat(exceptionText(exception), not(containsString("BEGIN ENCRYPTED PRIVATE KEY")));
    }

    @Test
    void rejectsMalformedPrivateKeyWithoutLeakingIt() {
        String secretPrivateKey = "-----BEGIN PRIVATE KEY-----\nSECRET_MATERIAL\n-----END PRIVATE KEY-----";
        CertificateBundleWithPrivateKey bundle = CertificateBundleWithPrivateKey.builder()
                .versionNumber(1L)
                .certificatePem(resource("serverCert.pem"))
                .certChainPem("")
                .privateKeyPem(secretPrivateKey)
                .privateKeyPemPassphrase("another-secret")
                .build();

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> DefaultOciCertificatesDownloader.toCertificatesWithPrivateKey(response(bundle, null)));

        assertThat(exceptionText(exception), containsString("private key cannot be decoded"));
        assertThat(exceptionText(exception), not(containsString("SECRET_MATERIAL")));
        assertThat(exceptionText(exception), not(containsString("another-secret")));
    }

    @Test
    void rejectsMissingPrivateKey() {
        CertificateBundleWithPrivateKey bundle = CertificateBundleWithPrivateKey.builder()
                .versionNumber(1L)
                .certificatePem(resource("serverCert.pem"))
                .certChainPem("")
                .build();

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> DefaultOciCertificatesDownloader.toCertificatesWithPrivateKey(response(bundle, null)));

        assertThat(exception.getMessage(), containsString("private key is missing"));
    }

    @Test
    void rejectsMissingCertificate() {
        CertificateBundleWithPrivateKey bundle = CertificateBundleWithPrivateKey.builder()
                .versionNumber(1L)
                .certChainPem("")
                .privateKeyPem(resource("serverKey.pem"))
                .build();

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> DefaultOciCertificatesDownloader.toCertificatesWithPrivateKey(response(bundle, null)));

        assertThat(exception.getMessage(), containsString("leaf certificate is missing"));
    }

    @Test
    void rejectsMalformedCertificateWithoutLeakingIt() {
        String secretCertificate = "-----BEGIN CERTIFICATE-----\nSECRET_CERTIFICATE\n-----END CERTIFICATE-----";
        CertificateBundleWithPrivateKey bundle = CertificateBundleWithPrivateKey.builder()
                .versionNumber(1L)
                .certificatePem(secretCertificate)
                .certChainPem("")
                .privateKeyPem(resource("serverKey.pem"))
                .build();

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> DefaultOciCertificatesDownloader.toCertificatesWithPrivateKey(response(bundle, null)));

        assertThat(exceptionText(exception), containsString("leaf certificate cannot be decoded"));
        assertThat(exceptionText(exception), not(containsString("SECRET_CERTIFICATE")));
    }

    private static CertificatesWithPrivateKey decode(String certificate,
                                                      String chain,
                                                      String privateKey,
                                                      String passphrase,
                                                      Long version,
                                                      String etag) {
        CertificateBundleWithPrivateKey bundle = CertificateBundleWithPrivateKey.builder()
                .versionNumber(version)
                .certificatePem(resource(certificate))
                .certChainPem(chain.isEmpty() ? "" : resource(chain))
                .privateKeyPem(resource(privateKey))
                .privateKeyPemPassphrase(passphrase)
                .build();
        return DefaultOciCertificatesDownloader.toCertificatesWithPrivateKey(response(bundle, etag));
    }

    private static GetCertificateBundleResponse response(CertificateBundle bundle, String etag) {
        return GetCertificateBundleResponse.builder()
                .__httpStatusCode__(200)
                .etag(etag)
                .certificateBundle(bundle)
                .build();
    }

    private static String resource(String name) {
        try (InputStream input = DefaultOciCertificatesDownloaderTest.class.getResourceAsStream(TEST_KEYS + name)) {
            if (input == null) {
                throw new IllegalArgumentException("Missing test resource: " + name);
            }
            return new String(input.readAllBytes(), StandardCharsets.US_ASCII);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read test resource: " + name, e);
        }
    }

    private static String exceptionText(Throwable throwable) {
        StringBuilder result = new StringBuilder();
        while (throwable != null) {
            result.append(throwable.getMessage());
            throwable = throwable.getCause();
        }
        return result.toString();
    }

    @AuthCachingPolicy(cacheKeyId = false, cachePrivateKey = false)
    private static final class RequestCapturingAuthProvider
            implements BasicAuthenticationDetailsProvider, RegionProvider, ProvidesClientConfigurators {
        private final AtomicReference<URI> capturedUri;

        private RequestCapturingAuthProvider(AtomicReference<URI> capturedUri) {
            this.capturedUri = capturedUri;
        }

        @Override
        public Region getRegion() {
            return Region.US_PHOENIX_1;
        }

        @Override
        public List<ClientConfigurator> getClientConfigurators() {
            return List.of(builder -> builder.registerRequestInterceptor(
                    Priorities.AUTHENTICATION - 1,
                    request -> {
                        if (!capturedUri.compareAndSet(null, request.uri())) {
                            throw new AssertionError("OCI request was attempted more than once");
                        }
                        throw new RequestCapturedException();
                    }));
        }

        @Override
        public String getKeyId() {
            throw signingWasReached();
        }

        @Override
        public InputStream getPrivateKey() {
            throw signingWasReached();
        }

        @Deprecated
        @Override
        public String getPassPhrase() {
            throw signingWasReached();
        }

        @Override
        public char[] getPassphraseCharacters() {
            throw signingWasReached();
        }

        private static AssertionError signingWasReached() {
            return new AssertionError("OCI request signing ran before request capture");
        }
    }

    private static final class RequestCapturedException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private static final String MESSAGE = "OCI request captured before authentication and network access";

        private RequestCapturedException() {
            super(MESSAGE);
        }
    }
}
