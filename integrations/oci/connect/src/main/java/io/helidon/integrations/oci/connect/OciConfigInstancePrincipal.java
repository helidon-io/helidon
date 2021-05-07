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

package io.helidon.integrations.oci.connect;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.json.Json;
import javax.json.JsonBuilderFactory;
import javax.json.JsonObject;
import javax.json.JsonWriterFactory;

import io.helidon.common.Version;
import io.helidon.common.configurable.Resource;
import io.helidon.common.http.Http;
import io.helidon.common.http.MediaType;
import io.helidon.common.pki.KeyConfig;
import io.helidon.common.reactive.Single;
import io.helidon.media.jsonp.JsonpSupport;
import io.helidon.security.Security;
import io.helidon.webclient.WebClient;
import io.helidon.webclient.WebClientResponse;
import io.helidon.webclient.security.WebClientSecurity;

/**
 * OCI connectivity configuration based on instance principal.
 * This is used when running within OCI VMs.
 * <p>
 * Configuration:
 * <a href="https://docs.oracle.com/en-us/iaas/Content/Identity/Tasks/callingservicesfrominstances.htm">OCI Instance Security</a>
 */
public class OciConfigInstancePrincipal implements OciConfigProvider {
    private static final Logger LOGGER = Logger.getLogger(OciConfigInstancePrincipal.class.getName());
    private static final String DEFAULT_METADATA_SERVICE_URL = "http://169.254.169.254/opc/v2/";
    private static final JsonBuilderFactory JSON = Json.createBuilderFactory(Map.of());
    private static final JsonWriterFactory JSON_WRITER_FACTORY = Json.createWriterFactory(Map.of());
    private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();

    private final AtomicReference<OciSignatureData> currentSignatureData = new AtomicReference<>();
    private final String region;
    private final String tenancyId;
    private final SessionKeys sessionKeys;
    private final Supplier<Single<PrivateKey>> privateKeySupplier;
    private final Supplier<Single<X509Certificate>> certificateSupplier;
    private final Supplier<Single<X509Certificate>> intermediateCertificateSupplier;
    private final WebClient federationClient;
    private final OciOutboundSecurityProvider securityProvider;
    private final AtomicReference<CompletionStage<OciSignatureData>> refreshFuture = new AtomicReference<>();
    private final AtomicReference<Instant> lastSuccessfulRefresh = new AtomicReference<>(Instant.now());

    private OciConfigInstancePrincipal(Builder builder) {
        this.region = builder.region;
        this.tenancyId = builder.tenant;
        this.sessionKeys = builder.sessionKeys;
        this.privateKeySupplier = builder.privateKeySupplier;
        this.certificateSupplier = builder.certificateSupplier;
        this.intermediateCertificateSupplier = builder.intermediateCertSupplier;
        this.federationClient = builder.federationClient;
        this.securityProvider = builder.securityProvider;

        // this is blocking intentionally - when starting the service, we want to make sure
        // instance principal data is available
        this.currentSignatureData.set(getData(sessionKeys.keyPair(),
                                              certificateSupplier.get().await(10, TimeUnit.SECONDS),
                                              intermediateCertificateSupplier.get().await(10, TimeUnit.SECONDS))
                                              .await(10, TimeUnit.SECONDS));
    }

    // this method blocks when trying to connect to a remote IP address
    static boolean isAvailable() {
        return WebClient.builder()
                        .connectTimeout(1, TimeUnit.SECONDS)
                        .readTimeout(1, TimeUnit.SECONDS)
                        .baseUri(DEFAULT_METADATA_SERVICE_URL)
                        .followRedirects(true)
                        .keepAlive(false)
                        .build()
                        .get()
                        .request()
                        .map(response -> response.status() == Http.Status.FORBIDDEN_403)
                        .onErrorResume(it -> false)
                        .await();
    }

    /**
     * Fluent API builder to create customized instances.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Create a new instance from environment.
     *
     * @return a new instance
     */
    public static OciConfigInstancePrincipal create() {
        return builder().build();
    }

    private static String fingerprint(X509Certificate leafCertificate) {
        try {
            byte[] encoded = leafCertificate.getEncoded();
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(encoded);
            return hexEncode(digest, ":");
        } catch (Exception e) {
            throw new OciApiException("Failed to get certificate fingerprint " + leafCertificate, e);
        }
    }

    private static String hexEncode(byte[] bytes, String separator) {
        List<String> encoded = new ArrayList<>(bytes.length);

        for (byte aByte : bytes) {
            int v = aByte & 0xFF;
            encoded.add("" + HEX_ARRAY[v >>> 4] + HEX_ARRAY[v & 0x0F]);
        }

        return String.join(separator, encoded);
    }

    @Override
    public OciSignatureData signatureData() {
        return currentSignatureData.get();
    }

    @Override
    public String region() {
        return region;
    }

    @Override
    public String tenancyOcid() {
        return tenancyId;
    }

    @Override
    public Single<OciSignatureData> refresh() {

        // we must use future, as there may be multiple singles created from it
        CompletableFuture<OciSignatureData> nextFuture = new CompletableFuture<>();

        CompletionStage<OciSignatureData> inProgress = refreshFuture.compareAndExchange(null, nextFuture);
        if (inProgress != null) {
            LOGGER.fine("Refresh already in progress.");
            // I do not own this refresh
            return Single.create(inProgress);
        }

        Instant instant = lastSuccessfulRefresh.get();
        if (instant != null && instant.plus(1, ChronoUnit.MINUTES).isAfter(Instant.now())) {
            LOGGER.fine("Refresh requested within one minute of last successful refresh, ignoring");
            refreshFuture.set(null);
            return Single.just(currentSignatureData.get());
        }

        LOGGER.fine("Refresh of signature data initialized");
        nextFuture.handle((it, throwable) -> {
            if (throwable != null) {
                LOGGER.fine("Finished refresh with exception: " + throwable.getMessage() + ", stack trace available in FINEST");
                LOGGER.log(Level.FINEST, "Exception", throwable);
            } else {
                lastSuccessfulRefresh.set(Instant.now());
                LOGGER.fine("Finished refresh successfully. New kid: " + it.keyId());
            }
            // now we can open for next refresh command
            refreshFuture.set(null);
            return null;
        });

        // we may have parallel requests to refresh under heavy load
        KeyPair keyPair = sessionKeys.refresh();

        // trigger read in parallel
        Single<PrivateKey> pkSingle = privateKeySupplier.get();
        Single<X509Certificate> certSingle = certificateSupplier.get();
        Single<X509Certificate> intermediateSingle = intermediateCertificateSupplier.get();

        pkSingle.flatMapSingle(privateKey -> certSingle
                .flatMapSingle(certificate -> intermediateSingle
                        .flatMapSingle(intermediateCert -> refresh(keyPair, privateKey, certificate, intermediateCert))))
                .forSingle(nextFuture::complete)
                .exceptionallyAccept(nextFuture::completeExceptionally);

        return Single.create(nextFuture);
    }

    private Single<OciSignatureData> refresh(KeyPair keyPair,
                                             PrivateKey privateKey,
                                             X509Certificate leafCertificate,
                                             X509Certificate intermediateCert) {

        OciSignatureData current = currentSignatureData.get();

        String keyId = tenancyId + "/fed-x509-sha256/" + fingerprint(leafCertificate);
        securityProvider.updateSignatureData(OciSignatureData.create(keyId,
                                                                     (RSAPrivateKey) privateKey));

        Single<OciSignatureData> response = getData(keyPair, leafCertificate, intermediateCert)
                .peek(currentSignatureData::set);

        if (current == null) {
            return response;
        } else {
            // if all fails, return current data
            return response.onErrorResume(throwable -> {
                LOGGER.log(Level.WARNING, "Failed to refresh instance principal token", throwable);
                return current;
            });
        }
    }

    // only get the new signature data
    private Single<OciSignatureData> getData(KeyPair keyPair,
                                             X509Certificate leafCertificate,
                                             X509Certificate intermediateCert) {

        LOGGER.fine("Getting signature data");

        PublicKey publicKey = keyPair.getPublic();

        String publicKeyPem = toPem(publicKey);
        String leafCertificatePem = toPem(leafCertificate);
        String intermediatePem = toPem(intermediateCert);
        String purpose = "DEFAULT";
        String fingerprintAlgorithm = "SHA256";

        JsonObject jsonRequest = JSON.createObjectBuilder()
                                     .add("publicKey", publicKeyPem)
                                     .add("certificate", leafCertificatePem)
                                     .add("intermediateCertificates", JSON.createArrayBuilder().add(intermediatePem))
                                     .add("purpose", purpose)
                                     .add("fingerprintAlgorithm", fingerprintAlgorithm)
                                     .build();

        // this requires a content length and hash
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        JSON_WRITER_FACTORY.createWriter(baos).write(jsonRequest);

        byte[] requestBytes = baos.toByteArray();
        String sha256 = OciRestApi.computeSha256(requestBytes);

        return federationClient.post()
                               .path("/v1/x509")
                               .contentType(MediaType.APPLICATION_JSON)
                               .accept(MediaType.APPLICATION_JSON)
                               .headers(headers -> {
                                   headers.contentLength(requestBytes.length);
                                   headers.add("x-content-sha256", sha256);
                                   return headers;
                               })
                               .submit(requestBytes)
                               .flatMapSingle(it -> {
                                   if (it.status()
                                         .family() != Http.ResponseStatus.Family.SUCCESSFUL) {
                                       return readError(it);
                                   }
                                   return it.content().as(JsonObject.class);
                               })
                               .map(it -> it.getString("token"))
                               .map(newToken -> toData(newToken, keyPair));
    }

    private OciSignatureData toData(String newToken, KeyPair keyPair) {
        return OciSignatureData.create("ST$" + newToken, (RSAPrivateKey) keyPair.getPrivate());
    }

    private Single<JsonObject> readError(WebClientResponse response) {
        return response.content()
                       .as(String.class)
                       .flatMapSingle(entity -> Single
                               .error(OciRestException.builder()
                                                      .headers(response.headers())
                                                      .status(response.status())
                                                      .message(entity)
                                                      .build()));
    }

    private String toPem(X509Certificate certificate) {
        try {
            return Base64.getEncoder().encodeToString(certificate.getEncoded());
        } catch (CertificateEncodingException e) {
            throw new OciApiException("Failed to encode certificate " + certificate, e);
        }
    }

    private String toPem(PublicKey publicKey) {
        return Base64.getEncoder().encodeToString(publicKey.getEncoded());
    }

    /**
     * Fluent API builder for {@link io.helidon.integrations.oci.connect.OciConfigInstancePrincipal}.
     */
    public static class Builder implements io.helidon.common.Builder<OciConfigInstancePrincipal> {
        private static final Logger LOGGER = Logger.getLogger(Builder.class.getName());
        private final WebClient.Builder webClientBuilder = WebClient.builder()
                                                                    .followRedirects(true);
        private final WebClient.Builder federationClientBuilder = WebClient.builder()
                                                                           .followRedirects(true);

        private String metadataServiceUrl = DEFAULT_METADATA_SERVICE_URL;
        private WebClient webClient;
        private WebClient federationClient;
        private String region;
        private String federationEndpoint;
        private String tenant;
        private Supplier<Single<X509Certificate>> certificateSupplier;
        private Supplier<Single<PrivateKey>> privateKeySupplier;
        private Supplier<Single<X509Certificate>> intermediateCertSupplier;
        private SessionKeys sessionKeys;
        private OciOutboundSecurityProvider securityProvider;

        private Builder() {
        }

        private static Single<PrivateKey> getPrivateKey(WebClient webClient) {
            LOGGER.finest("Looking up private key");

            return webClient.get()
                            .path("/identity/key.pem")
                            .request(String.class)
                            .map(Builder::toPrivateKey);
        }

        private static PrivateKey toPrivateKey(String pemEncoded) {
            return KeyConfig.pemBuilder()
                            .key(Resource.create("identity/key.pem", pemEncoded))
                            .build()
                            .privateKey()
                            .orElseThrow(() -> new OciApiException("Could not load private key from identity/key.pem"));
        }

        private static Single<X509Certificate> getIntermediateCertificate(WebClient webClient) {
            LOGGER.finest("Looking up intermediate certificate");

            return webClient.get()
                            .path("/identity/intermediate.pem")
                            .request(String.class)
                            .map(Builder::toX509Cert);
        }

        private static Single<X509Certificate> getCertificate(WebClient webClient) {
            LOGGER.finest("Looking up certificate");

            return webClient.get()
                            .path("/identity/cert.pem")
                            .request(String.class)
                            .map(Builder::toX509Cert);
        }

        private static X509Certificate toX509Cert(String pemEncoded) {
            try {
                CertificateFactory factory = CertificateFactory.getInstance("X.509");
                return (X509Certificate) factory
                        .generateCertificate(new ByteArrayInputStream(pemEncoded.getBytes(StandardCharsets.UTF_8)));
            } catch (CertificateException e) {
                throw new OciApiException("Failed to parse certificate for instance principal", e);
            }
        }

        @Override
        public OciConfigInstancePrincipal build() {
            if (webClient == null) {
                webClient = webClientBuilder.baseUri(metadataServiceUrl)
                                            .addHeader(Http.Header.ACCEPT, MediaType.TEXT_PLAIN.toString())
                                            .addHeader(Http.Header.AUTHORIZATION, "Bearer Oracle")
                                            .build();
            }

            // get region
            if (region == null) {
                region = getRegion();
            }
            LOGGER.fine("Using region " + region);

            // endpoint for authentication service
            if (federationEndpoint == null) {
                federationEndpoint = "https://auth." + region + ".oraclecloud.com";
            }
            LOGGER.fine("Using federation endpoint " + federationEndpoint);

            if (certificateSupplier == null) {
                certificateSupplier = () -> getCertificate(webClient);
            }

            if (privateKeySupplier == null) {
                privateKeySupplier = () -> getPrivateKey(webClient);
            }

            if (intermediateCertSupplier == null) {
                intermediateCertSupplier = () -> getIntermediateCertificate(webClient);
            }
            if (sessionKeys == null) {
                sessionKeys = SessionKeys.create();
            }

            // we need certificate for initial setup
            X509Certificate certificate = certificateSupplier.get().await(10, TimeUnit.SECONDS);

            if (tenant == null) {
                // now we need certificate and tenancy ID
                Map<String, List<String>> name = parseName(certificate.getSubjectX500Principal().getName());
                tenant = getOu(name, "opc-tenant:")
                        .or(() -> getO(name, "opc-identity:"))
                        .orElseThrow(() -> new OciApiException("Could not identify instance tenant from certificate."));

            }

            String keyId = tenant + "/fed-x509-sha256/" + fingerprint(certificate);
            RSAPrivateKey initialPrivateKey = (RSAPrivateKey) privateKeySupplier.get().await(10, TimeUnit.SECONDS);
            this.securityProvider = OciOutboundSecurityProvider.create(OciSignatureData.create(keyId, initialPrivateKey));

            Security security = Security.builder()
                                        .addOutboundSecurityProvider(securityProvider)
                                        .build();

            // federation client
            if (federationClient == null) {
                URI federationUri = URI.create(federationEndpoint);

                federationClient = federationClientBuilder.addMediaSupport(JsonpSupport.create())
                                                          .baseUri(federationEndpoint)
                                                          .addHeader("opc-client-info", "Helidon/" + Version.VERSION)
                                                          .addHeader("host", federationUri.getHost())
                                                          .addService(WebClientSecurity.create(security))
                                                          .build();
            }

            return new OciConfigInstancePrincipal(this);
        }

        /**
         * Update web client builder.
         * This can be used to configure
         * {@link io.helidon.webclient.WebClient.Builder#connectTimeout(long, java.util.concurrent.TimeUnit)},
         * {@link io.helidon.webclient.WebClient.Builder#readTimeout(long, java.util.concurrent.TimeUnit)} and other options.
         *
         * @param updater consumer that updates the web client builder
         * @return updated builder instance
         */
        public Builder webClientBuilder(Consumer<WebClient.Builder> updater) {
            updater.accept(this.webClientBuilder);
            return this;
        }

        /**
         * Configure custom metadata service URL.
         *
         * @param metadataServiceUrl URL of the service, if not defined, uses {@value #DEFAULT_METADATA_SERVICE_URL}
         * @return updated builder
         */
        public Builder metadataServiceUrl(String metadataServiceUrl) {
            this.metadataServiceUrl = metadataServiceUrl;
            return this;
        }

        /**
         * Configure region to use.
         *
         * @param region region identifier
         * @return updated builder
         */
        public Builder region(String region) {
            this.region = region;
            return this;
        }

        /**
         * Configure an explicit federation endpoint. If not defined, it is constructed with region.
         *
         * @param federationEndpoint federation endpoint
         * @return updated builder
         */
        public Builder federationEndpoint(String federationEndpoint) {
            this.federationEndpoint = federationEndpoint;
            return this;
        }

        private Optional<String> getO(Map<String, List<String>> name, String prefix) {
            return getFromName(name.get("O"), prefix);
        }

        private Optional<String> getOu(Map<String, List<String>> name, String prefix) {
            return getFromName(name.get("OU"), prefix);
        }

        private Optional<String> getFromName(List<String> values, String prefix) {
            if (values == null) {
                return Optional.empty();
            }

            for (String value : values) {
                if (value.startsWith(prefix)) {
                    return Optional.of(value.substring(prefix.length()));
                }
            }

            return Optional.empty();
        }

        private Map<String, List<String>> parseName(String name) {
            Map<String, List<String>> result = new HashMap<>();
            //name is a=b,b=c,d=f
            String[] parts = name.split(",");
            for (String part : parts) {
                int equals = part.indexOf('=');
                if (equals > 0) {
                    String key = part.substring(0, equals).trim();
                    String value = part.substring(equals + 1).trim();

                    LOGGER.finest("Found name part: " + key + "=" + value);
                    result.computeIfAbsent(key.toUpperCase(), it -> new LinkedList<>()).add(value);
                } else {
                    LOGGER.fine("Could not understand name part " + part);
                }
            }

            return result;
        }

        private String getRegion() {
            LOGGER.finest("Looking up region using " + metadataServiceUrl);

            // returns string, such as eu-frankfurt-1
            return webClient.get()
                            .path("/instance/region")
                            .request(String.class)
                            .await(10, TimeUnit.SECONDS);
        }
    }
}
