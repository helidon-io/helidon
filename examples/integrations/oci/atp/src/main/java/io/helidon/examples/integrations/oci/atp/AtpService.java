/*
 * Copyright (c) 2021, 2023 Oracle and/or its affiliates.
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

package io.helidon.examples.integrations.oci.atp;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.sql.SQLException;
import java.time.Duration;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import io.helidon.common.http.Http;
import io.helidon.config.Config;
import io.helidon.config.ConfigException;
import io.helidon.nima.webserver.http.HttpRules;
import io.helidon.nima.webserver.http.HttpService;
import io.helidon.nima.webserver.http.ServerRequest;
import io.helidon.nima.webserver.http.ServerResponse;
import io.helidon.reactive.dbclient.DbClient;
import io.helidon.reactive.dbclient.jdbc.JdbcDbClientProvider;

import com.oracle.bmc.ConfigFileReader;
import com.oracle.bmc.auth.AuthenticationDetailsProvider;
import com.oracle.bmc.auth.ConfigFileAuthenticationDetailsProvider;
import com.oracle.bmc.database.Database;
import com.oracle.bmc.database.DatabaseClient;
import com.oracle.bmc.database.model.GenerateAutonomousDatabaseWalletDetails;
import com.oracle.bmc.database.requests.GenerateAutonomousDatabaseWalletRequest;
import com.oracle.bmc.database.responses.GenerateAutonomousDatabaseWalletResponse;
import oracle.jdbc.pool.OracleDataSource;
import oracle.security.pki.OraclePKIProvider;
import oracle.ucp.jdbc.PoolDataSource;
import oracle.ucp.jdbc.PoolDataSourceFactory;

class AtpService implements HttpService {
    private static final Logger LOGGER = Logger.getLogger(AtpService.class.getName());

    private final Database databaseAsyncClient;
    private final Config config;

    AtpService(Config config) {
        try {
            // this requires OCI configuration in the usual place
            // ~/.oci/config
            AuthenticationDetailsProvider authProvider = new ConfigFileAuthenticationDetailsProvider(ConfigFileReader.parseDefault());
            databaseAsyncClient = DatabaseClient.builder().build(authProvider);
            this.config = config;
        } catch (IOException e) {
            throw new ConfigException("Failed to read configuration properties", e);
        }
    }

    /**
     * A service registers itself by updating the routine rules.
     *
     * @param rules the routing rules.
     */
    public void routing(HttpRules rules) {
        rules.get("/wallet", this::generateWallet);
    }

    /**
     * Generate wallet file for the configured ATP.
     *
     * @param req request
     * @param res response
     */
    private void generateWallet(ServerRequest req, ServerResponse res) {
        GenerateAutonomousDatabaseWalletResponse walletResponse = databaseAsyncClient.generateAutonomousDatabaseWallet(
                GenerateAutonomousDatabaseWalletRequest.builder()
                        .autonomousDatabaseId(config.get("oci.atp.ocid").asString().get())
                        .generateAutonomousDatabaseWalletDetails(
                                GenerateAutonomousDatabaseWalletDetails.builder()
                                        .password(config.get("oci.atp.walletPassword").asString().get())
                                        .build())
                        .build());

        if (walletResponse.getContentLength() == 0) {
            LOGGER.log(Level.SEVERE, "GenerateAutonomousDatabaseWalletResponse is empty");
            res.status(Http.Status.NOT_FOUND_404).send();
            return;
        }

        byte[] walletContent = null;
        try {
            walletContent = walletResponse.getInputStream().readAllBytes();
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error processing GenerateAutonomousDatabaseWalletResponse", e);
            res.status(Http.Status.INTERNAL_SERVER_ERROR_500).send(e.getMessage());
            return;
        }

        try {
            String result = createDbClient(walletContent)
                    .execute(exec -> exec.query("SELECT 'Hello world!!' FROM DUAL"))
                    .first()
                    .map(dbRow -> dbRow.column(1).as(String.class))
                    .await(Duration.ofSeconds(60));
            if (result == null || result.isEmpty()) {
                res.status(Http.Status.NOT_FOUND_404).send();
            } else {
                res.send(result);
            }
        } catch (Exception e) {
            res.status(Http.Status.INTERNAL_SERVER_ERROR_500).send(e.getMessage());
        }
    }

    DbClient createDbClient(byte[] walletContent) {
        PoolDataSource pds = PoolDataSourceFactory.getPoolDataSource();
        try {
            pds.setSSLContext(getSSLContext(walletContent));
            pds.setURL(getJdbcUrl(walletContent, config.get("db.tnsNetServiceName")
                    .as(String.class)
                    .orElseThrow(() -> new IllegalStateException("Missing tnsNetServiceName!!"))));
            pds.setUser(config.get("db.userName").as(String.class).orElse("ADMIN"));
            pds.setPassword(config.get("db.password")
                    .as(String.class)
                    .orElseThrow(() -> new IllegalStateException("Missing password!!")));
            pds.setConnectionFactoryClassName(OracleDataSource.class.getName());
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error setting up PoolDataSource", e);
            throw new RuntimeException(e);
        }

        return new JdbcDbClientProvider().builder()
                .connectionPool(() -> {
                    try {
                        return pds.getConnection();
                    } catch (SQLException e) {
                        throw new IllegalStateException("Error while setting up new connection", e);
                    }
                })
                .build();
    }

    /**
     * Returns SSLContext based on cwallet.sso in wallet.
     *
     * @return SSLContext
     */
    private static SSLContext getSSLContext(byte[] walletContent) throws IllegalStateException {
        SSLContext sslContext = null;
        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new ByteArrayInputStream(walletContent)))) {
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
        } catch (RuntimeException | Error throwMe) {
            throw throwMe;
        } catch (Exception e) {
            throw new IllegalStateException("Error while getting SSLContext from wallet.", e);
        }
        return sslContext;
    }

    /**
     * Returns JDBC URL with connection description for the given service based on tnsnames.ora in wallet.
     *
     * @param walletContent     walletContent
     * @param tnsNetServiceName tnsNetServiceName
     * @return String
     */
    private static String getJdbcUrl(byte[] walletContent, String tnsNetServiceName) throws IllegalStateException {
        String jdbcUrl = null;
        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new ByteArrayInputStream(walletContent)))) {
            ZipEntry entry = null;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().equals("tnsnames.ora")) {
                    jdbcUrl = new String(zis.readAllBytes(), StandardCharsets.UTF_8)
                            .replaceFirst(tnsNetServiceName + "\\s*=\\s*", "jdbc:oracle:thin:@")
                            .replaceAll("\\n[^\\n]+", "");
                }
                zis.closeEntry();
            }
        } catch (IOException e) {
            throw new IllegalStateException("Error while getting JDBC URL from wallet.", e);
        }
        return jdbcUrl;
    }
}
