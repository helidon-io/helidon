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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import io.helidon.common.configurable.Resource;
import io.helidon.common.pki.Keys;
import io.helidon.common.pki.PemReader;
import io.helidon.integrations.oci.tls.certificates.spi.OciCertificatesDownloader;
import io.helidon.integrations.oci.tls.certificates.spi.OciCertificatesDownloader.Certificates;
import io.helidon.integrations.oci.tls.certificates.spi.OciCertificatesDownloader.CertificatesWithPrivateKey;

import com.oracle.bmc.Region;
import com.oracle.bmc.auth.AuthCachingPolicy;
import com.oracle.bmc.auth.BasicAuthenticationDetailsProvider;
import com.oracle.bmc.auth.ProvidesClientConfigurators;
import com.oracle.bmc.auth.RegionProvider;
import com.oracle.bmc.certificates.model.CertificateBundle;
import com.oracle.bmc.certificates.model.CertificateBundlePublicOnly;
import com.oracle.bmc.certificates.model.CertificateBundleWithPrivateKey;
import com.oracle.bmc.http.ClientConfigurator;
import com.oracle.bmc.http.Priorities;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
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
    void requestsExplicitCurrentPublicBundle() {
        AtomicReference<URI> capturedUri = new AtomicReference<>();
        DefaultOciCertificatesDownloader downloader =
                new DefaultOciCertificatesDownloader(new RequestCapturingAuthProvider(capturedUri));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> downloader.loadCertificates("certificate-ocid"));

        assertThat(exceptionText(exception), containsString(RequestCapturedException.MESSAGE));
        URI requestUri = capturedUri.get();
        assertThat(requestUri, notNullValue());
        assertThat(requestUri.getRawPath(), is("/20210224/certificateBundles/certificate-ocid"));
        assertThat(Arrays.asList(requestUri.getRawQuery().split("&")),
                   containsInAnyOrder("stage=CURRENT", "certificateBundleType=CERTIFICATE_CONTENT_PUBLIC_ONLY"));
    }

    @Test
    void createsEndpointForIpv6Literal() throws IOException {
        URI endpoint = BundleResponseServer.endpoint(InetAddress.getByName("::1"), 8443);

        assertThat(endpoint.getScheme(), is("http"));
        assertThat(endpoint.getHost(), containsString(":"));
        assertThat(endpoint.getPort(), is(8443));
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
    void publicAndPrivateBundlesUseComparableVersionNumbers() {
        CertificateBundlePublicOnly publicBundle = CertificateBundlePublicOnly.builder()
                .versionNumber(42L)
                .certificatePem(resource("serverCert.pem"))
                .certChainPem(resource("ca.pem"))
                .build();
        Certificates publicCertificates = loadPublicBundle(publicBundle, "public-etag");
        CertificatesWithPrivateKey privateCertificates =
                decode("serverCert.pem", "", "serverKey.pem", null, 42L, "private-etag");

        assertThat(publicCertificates.version(), is(privateCertificates.version()));
        assertThat(publicCertificates.version(), is("42"));
    }

    @Test
    void fallsBackFromVersionNumberToEtagAndCertificateHash() {
        CertificatesWithPrivateKey result = decode("serverCert.pem", "", "serverKey.pem", null, null, "etag");
        assertThat(result.version(), is("etag"));

        X509Certificate certificate = result.certificates()[0];
        CertificatesWithPrivateKey hashResult = decode("serverCert.pem", "", "serverKey.pem", null, null, null);
        assertThat(hashResult.version(), is(String.valueOf(Arrays.hashCode(new X509Certificate[] {certificate}))));
    }

    @Test
    void rejectsPublicOnlyBundle() {
        CertificateBundlePublicOnly response = CertificateBundlePublicOnly.builder()
                .certificatePem(resource("serverCert.pem"))
                .certChainPem("")
                .build();

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> loadPrivateKeyBundle(response, "etag"));

        assertThat(exceptionText(exception), containsString("does not contain a private key"));
    }

    @Test
    void rejectsMismatchedPrivateKey() {
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> decode("serverCert.pem", "", "ecKey.pem", null, 1L, null));

        assertThat(exceptionText(exception), containsString("does not match the leaf certificate"));
    }

    @Test
    void rejectsMismatchedPrivateKeyFromCustomDownloader() {
        OciCertificatesDownloader downloader = new CustomDownloader(
                certificate("serverCert.pem"),
                privateKey("ecKey.pem"));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> downloader.loadCertificatesWithPrivateKey("certificate-ocid"));

        assertThat(exceptionText(exception), containsString("does not match the leaf certificate"));
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
                () -> loadPrivateKeyBundle(bundle, null));

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
                () -> loadPrivateKeyBundle(bundle, null));

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
                () -> loadPrivateKeyBundle(bundle, null));

        assertThat(exceptionText(exception), containsString("private key is missing"));
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
                () -> loadPrivateKeyBundle(bundle, null));

        assertThat(exceptionText(exception), containsString("leaf certificate is missing"));
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
                () -> loadPrivateKeyBundle(bundle, null));

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
        return loadPrivateKeyBundle(bundle, etag);
    }

    private static CertificatesWithPrivateKey loadPrivateKeyBundle(CertificateBundle bundle, String etag) {
        return loadBundle(bundle, etag, downloader -> downloader.loadCertificatesWithPrivateKey("certificate-ocid"));
    }

    private static Certificates loadPublicBundle(CertificateBundle bundle, String etag) {
        return loadBundle(bundle, etag, downloader -> downloader.loadCertificates("certificate-ocid"));
    }

    private static <T> T loadBundle(CertificateBundle bundle,
                                    String etag,
                                    Function<DefaultOciCertificatesDownloader, T> loader) {
        try (BundleResponseServer server = new BundleResponseServer(bundleJson(bundle), etag)) {
            DefaultOciCertificatesDownloader downloader =
                    new DefaultOciCertificatesDownloader(new LocalAuthProvider(server.endpoint()));
            return loader.apply(downloader);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to start the test OCI Certificates service", e);
        }
    }

    private static String bundleJson(CertificateBundle bundle) {
        boolean includesPrivateKey = bundle instanceof CertificateBundleWithPrivateKey;
        StringBuilder result = new StringBuilder("{")
                .append("\"certificateBundleType\":")
                .append(jsonValue(includesPrivateKey
                                          ? "CERTIFICATE_CONTENT_WITH_PRIVATE_KEY"
                                          : "CERTIFICATE_CONTENT_PUBLIC_ONLY"))
                .append(",\"versionNumber\":")
                .append(bundle.getVersionNumber())
                .append(",\"certificatePem\":")
                .append(jsonValue(bundle.getCertificatePem()))
                .append(",\"certChainPem\":")
                .append(jsonValue(bundle.getCertChainPem()));
        if (bundle instanceof CertificateBundleWithPrivateKey privateKeyBundle) {
            result.append(",\"privateKeyPem\":")
                    .append(jsonValue(privateKeyBundle.getPrivateKeyPem()))
                    .append(",\"privateKeyPemPassphrase\":")
                    .append(jsonValue(privateKeyBundle.getPrivateKeyPemPassphrase()));
        }
        return result.append('}').toString();
    }

    private static String jsonValue(String value) {
        if (value == null) {
            return "null";
        }
        return '"' + value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n") + '"';
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

    private static X509Certificate certificate(String name) {
        return PemReader.readCertificates(
                new ByteArrayInputStream(resource(name).getBytes(StandardCharsets.US_ASCII))).getFirst();
    }

    private static PrivateKey privateKey(String name) {
        return Keys.builder()
                .pem(pem -> pem.key(Resource.create("test private key", resource(name))))
                .build()
                .privateKey()
                .orElseThrow();
    }

    private static String exceptionText(Throwable throwable) {
        StringBuilder result = new StringBuilder();
        while (throwable != null) {
            result.append(throwable.getMessage());
            throwable = throwable.getCause();
        }
        return result.toString();
    }

    private static final class CustomDownloader implements OciCertificatesDownloader {
        private final X509Certificate certificate;
        private final PrivateKey privateKey;

        private CustomDownloader(X509Certificate certificate, PrivateKey privateKey) {
            this.certificate = certificate;
            this.privateKey = privateKey;
        }

        @Override
        public Certificates loadCertificates(String certOcid) {
            return OciCertificatesDownloader.create("1", new X509Certificate[] {certificate});
        }

        @Override
        public CertificatesWithPrivateKey loadCertificatesWithPrivateKey(String certOcid) {
            return OciCertificatesDownloader.create("1", new X509Certificate[] {certificate}, privateKey);
        }

        @Override
        public X509Certificate loadCACertificate(String caCertOcid) {
            return certificate;
        }
    }

    @AuthCachingPolicy(cacheKeyId = false, cachePrivateKey = false)
    private static final class LocalAuthProvider
            implements BasicAuthenticationDetailsProvider, RegionProvider, ProvidesClientConfigurators {
        private final URI endpoint;
        private final byte[] privateKey;

        private LocalAuthProvider(URI endpoint) {
            this.endpoint = endpoint;
            this.privateKey = resource("serverKey.pem").getBytes(StandardCharsets.US_ASCII);
        }

        @Override
        public Region getRegion() {
            return Region.US_PHOENIX_1;
        }

        @Override
        public List<ClientConfigurator> getClientConfigurators() {
            return List.of(builder -> builder.baseUri(endpoint));
        }

        @Override
        public String getKeyId() {
            return "test-key";
        }

        @Override
        public InputStream getPrivateKey() {
            return new ByteArrayInputStream(privateKey);
        }

        @Deprecated
        @Override
        public String getPassPhrase() {
            return null;
        }

        @Override
        public char[] getPassphraseCharacters() {
            return null;
        }
    }

    private static final class BundleResponseServer implements AutoCloseable {
        private final ServerSocket serverSocket;
        private final Thread serverThread;
        private final AtomicBoolean served = new AtomicBoolean();
        private final AtomicReference<Throwable> failure = new AtomicReference<>();

        private BundleResponseServer(String body, String etag) throws IOException {
            this.serverSocket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
            this.serverThread = Thread.ofPlatform()
                    .daemon()
                    .name("oci-certificates-test-server")
                    .start(() -> serve(body, etag));
        }

        @Override
        public void close() {
            try {
                serverSocket.close();
                serverThread.join(5000);
            } catch (IOException e) {
                throw new IllegalStateException("Failed to close the test OCI Certificates service", e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while stopping the test OCI Certificates service", e);
            }
            if (serverThread.isAlive()) {
                throw new IllegalStateException("Test OCI Certificates service did not stop");
            }
            if (failure.get() != null) {
                throw new IllegalStateException("Test OCI Certificates service failed", failure.get());
            }
            if (!served.get()) {
                throw new IllegalStateException("Test OCI Certificates service received no request");
            }
        }

        private static void readRequestHeaders(InputStream input) throws IOException {
            int matched = 0;
            int current;
            int[] endOfHeaders = {'\r', '\n', '\r', '\n'};
            while (matched < endOfHeaders.length && (current = input.read()) >= 0) {
                matched = current == endOfHeaders[matched] ? matched + 1 : 0;
            }
        }

        private URI endpoint() {
            return endpoint(serverSocket.getInetAddress(), serverSocket.getLocalPort());
        }

        private static URI endpoint(InetAddress address, int port) {
            try {
                return new URI("http", null, address.getHostAddress(), port, null, null, null);
            } catch (URISyntaxException e) {
                throw new IllegalStateException("Failed to create the test OCI Certificates service endpoint", e);
            }
        }

        private void serve(String body, String etag) {
            try (Socket socket = serverSocket.accept();
                    InputStream input = socket.getInputStream();
                    OutputStream output = socket.getOutputStream()) {
                readRequestHeaders(input);
                byte[] responseBody = body.getBytes(StandardCharsets.UTF_8);
                StringBuilder headers = new StringBuilder("HTTP/1.1 200 OK\r\n")
                        .append("Content-Type: application/json\r\n")
                        .append("Content-Length: ").append(responseBody.length).append("\r\n");
                if (etag != null) {
                    headers.append("etag: ").append(etag).append("\r\n");
                }
                headers.append("Connection: close\r\n\r\n");
                output.write(headers.toString().getBytes(StandardCharsets.US_ASCII));
                output.write(responseBody);
                output.flush();
                served.set(true);
            } catch (Throwable t) {
                if (!serverSocket.isClosed()) {
                    failure.set(t);
                }
            }
        }
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
