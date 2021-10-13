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

package io.helidon.integrations.oci.atp;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import io.helidon.integrations.oci.connect.OciRequestBase;
import io.helidon.integrations.oci.connect.OciResponseParser;

import oracle.security.pki.OraclePKIProvider;

/**
 * GenerateAutonomousDatabaseWallet request and response.
 */
public final class GenerateAutonomousDatabaseWallet {
    private GenerateAutonomousDatabaseWallet() {
    }

    /**
     * Request object. Can be configured with additional headers, query parameters etc.
     */
    public static class Request extends OciRequestBase<Request> {
        private Request() {
        }

        /**
         * Fluent API builder for configuring a request.
         * The request builder is passed as is, without a build method.
         * The equivalent of a build method is {@link #toJson(javax.json.JsonBuilderFactory)}
         * used by the {@link io.helidon.integrations.common.rest.RestApi}.
         *
         * @return new request builder
         */
        public static Request builder() {
            return new Request();
        }

        /**
         * Set explicit password to encrypt the keys inside the wallet.
         *
         * @param walletPassword walletPassword
         * @return updated request
         */
        public Request password(String walletPassword) {
            return add("password", walletPassword);
        }
    }

    /**
     * Response object parsed from JSON returned by the {@link io.helidon.integrations.common.rest.RestApi}.
     */
    public static class Response extends OciResponseParser {
        private final WalletArchive walletArchive;

        private Response(WalletArchive walletArchive) {
            this.walletArchive = walletArchive;
        }

        static Response create(byte[] bytes) {
            return new Response(new WalletArchive(bytes));
        }

        /**
         * Returns the wallet retrieved.
         *
         * @return WalletArchive - wallet data
         */
        public WalletArchive walletArchive() {
            return walletArchive;
        }

    }

    /**
     * Object to store wallet returned for ATP as bytes[].
     */
    public static class WalletArchive {
        private final byte[] content;

        /**
         * Set wallet data.
         *
         * @param content
         */
        public WalletArchive(byte[] content) {
            this.content = content.clone();
        }

        /**
         * Returns wallet data.
         *
         * @return bytes[]
         */
        public byte[] getContent() {
            return content.clone();
        }

        /**
         * Returns SSLContext based on cwallet.sso in wallet.
         *
         * @return SSLContext
         */
        public SSLContext getSSLContext() throws IllegalStateException {
            SSLContext sslContext = null;
            try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(content))) {
                ZipEntry entry = null;
                while ((entry = zis.getNextEntry()) != null) {
                    if (entry.getName().equals("cwallet.sso")) {
                        KeyStore keyStore = KeyStore.getInstance("SSO", new OraclePKIProvider());
                        keyStore.load(zis, null);
                        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance("PKIX");
                        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance("PKIX");
                        trustManagerFactory.init(keyStore);
                        keyManagerFactory.init(keyStore, null);
                        sslContext = SSLContext.getInstance("TLS");
                        sslContext.init(keyManagerFactory.getKeyManagers(), trustManagerFactory.getTrustManagers(), null);
                    }
                    zis.closeEntry();
                }
            } catch (Exception e) {
                throw new IllegalStateException("Error while getting SSLContext from wallet.", e);
            }
            return sslContext;
        }

        /**
         * Returns JDBC URL with connection description for the given service based on tnsnames.ora in wallet.
         *
         * @param serviceName
         * @return String
         */
        public String getJdbcUrl(String serviceName) throws IllegalStateException {
            String jdbcUrl = null;
            try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(content))) {
                ZipEntry entry = null;
                while ((entry = zis.getNextEntry()) != null) {
                    if (entry.getName().equals("tnsnames.ora")) {
                        jdbcUrl = new String(zis.readAllBytes(), StandardCharsets.UTF_8)
                                .replaceFirst(serviceName + "_high\\s+=\\s+", "jdbc:oracle:thin:@")
                                .replaceAll("\\n[^\\n]+", "");
                    }
                    zis.closeEntry();
                }
            } catch (Exception e) {
                throw new IllegalStateException("Error while getting JDBC URL from wallet.", e);
            }
            return jdbcUrl;
        }
    }
}
