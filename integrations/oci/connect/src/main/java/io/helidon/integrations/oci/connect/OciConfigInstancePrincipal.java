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
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.security.PrivateKey;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Logger;

import io.helidon.common.configurable.Resource;
import io.helidon.common.http.Http;
import io.helidon.common.http.MediaType;
import io.helidon.common.pki.KeyConfig;
import io.helidon.common.reactive.Single;
import io.helidon.webclient.WebClient;
import io.helidon.webserver.Routing;
import io.helidon.webserver.WebServer;

/**
 * OCI connectivity configuration based on instance principal.
 * This is used when running within OCI VMs.
 *
 * TODO THIS CLASS IS NOT YET IMPLEMENTED AND WILL THROW AN EXCEPTION
 */
public class OciConfigInstancePrincipal extends OciConfigPrincipalBase implements OciConfigProvider {
    private static final String DEFAULT_METADATA_SERVICE_URL = "http://169.254.169.254/opc/v2/";

    private final AtomicReference<OciSignatureData> currentSignatureData = new AtomicReference<>();
    private final String region;
    private final String tenancyId;

    private OciConfigInstancePrincipal(Builder builder) {
        super(builder);

        this.region = null;
        this.tenancyId = null;
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
     * Create a new instance from environment.
     *
     * @return a new instance
     */
    public static OciConfigInstancePrincipal create() {
        throw new UnsupportedOperationException("OCI instance principal authentication is not yet supported");
    }

    public static void main(String[] args) {
        WebServer webServer = WebServer.builder()
                                       .port(8080)
                                       .routing(Routing.builder()
                                                       .register("/opc/v2", new OciOpcService())
                                                       .build())
                                       .build()
                                       .start()
                                       .await();

        OciConfigInstancePrincipal cip = new Builder()
                .metadataServiceUrl("http://localhost:8080/opc/v2")
                .build();
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
        return OciConfigProvider.super.refresh();
    }

    /**
     * Fluent API builder for {@link io.helidon.integrations.oci.connect.OciConfigInstancePrincipal}.
     */
    public static class Builder extends OciConfigPrincipalBase.Builder<Builder>
            implements io.helidon.common.Builder<OciConfigInstancePrincipal> {
        private static final Logger LOGGER = Logger.getLogger(Builder.class.getName());

        private String metadataServiceUrl = DEFAULT_METADATA_SERVICE_URL;

        private WebClient.Builder webClientBuilder = WebClient.builder()
                                                              .followRedirects(true)
                                                              .keepAlive(false);
        private WebClient webClient;
        private String region;
        private String federationEndpoint;
        private String tenant;
        private Supplier<Single<X509Certificate>> certificateSupplier;
        private Supplier<Single<PrivateKey>> privateKeySupplier;
        private Supplier<Single<X509Certificate>> intermediateCertSupplier;

        @Override
        public OciConfigInstancePrincipal build() {
            webClientBuilder.baseUri(metadataServiceUrl);
            webClient = webClientBuilder.build();

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

            if (tenant == null) {
                // now we need certificate and tenancy ID
                X509Certificate certificate = getCertificate(webClient).await(10, TimeUnit.SECONDS);
                Map<String, List<String>> name = parseName(certificate.getSubjectX500Principal().getName());
                tenant = getOu(name, "opc-tenant:")
                        .or(() -> getO(name, "opc-identity:"))
                        .orElseThrow(() -> new OciApiException("Could not identify instance tenant from certificate."));

            }

            if (certificateSupplier == null) {
                certificateSupplier = () -> getCertificate(webClient);
            }
            if (privateKeySupplier == null) {
                privateKeySupplier = () -> getPrivateKey(webClient);
            }
            if (intermediateCertSupplier == null) {
                intermediateCertSupplier = () -> getIntermediateCertificate(webClient);
            }


            return new OciConfigInstancePrincipal(this);
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

        public Builder metadataServiceUrl(String metadataServiceUrl) {
            this.metadataServiceUrl = metadataServiceUrl;
            return this;
        }

        public Builder region(String region) {
            this.region = region;
            return this;
        }

        public Builder federationEndpoint(String federationEndpoint) {
            this.federationEndpoint = federationEndpoint;
            return this;
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

        private String getRegion() {
            LOGGER.finest("Looking up region using " + metadataServiceUrl);

            // returns string, such as eu-frankfurt-1
            return webClient.get()
                            .headers(headers -> {
                                headers.add(Http.Header.AUTHORIZATION, "Bearer Oracle");
                                headers.addAccept(MediaType.TEXT_PLAIN);
                                return headers;
                            })
                            .path("/instance/region")
                            .request(String.class)
                            .await(10, TimeUnit.SECONDS);
        }

        private WebClient webClient() {
            return webClient;
        }
    }
}
