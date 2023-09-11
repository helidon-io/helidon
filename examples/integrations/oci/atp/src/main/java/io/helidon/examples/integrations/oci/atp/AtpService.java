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
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import io.helidon.config.Config;
import io.helidon.dbclient.DbClient;
import io.helidon.dbclient.DbRow;
import io.helidon.dbclient.jdbc.JdbcClientProvider;
import io.helidon.http.Status;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.HttpService;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

import com.oracle.bmc.database.Database;
import com.oracle.bmc.database.model.GenerateAutonomousDatabaseWalletDetails;
import com.oracle.bmc.database.requests.GenerateAutonomousDatabaseWalletRequest;
import com.oracle.bmc.database.responses.GenerateAutonomousDatabaseWalletResponse;
import oracle.jdbc.pool.OracleDataSource;
import oracle.security.pki.OraclePKIProvider;
import oracle.ucp.jdbc.PoolDataSource;
import oracle.ucp.jdbc.PoolDataSourceFactory;

class AtpService implements HttpService {
    private static final Logger LOGGER = Logger.getLogger(AtpService.class.getName());

    private final Database databaseClient;
    private final Config config;

    AtpService(Database databaseClient, Config config) {
        this.databaseClient = databaseClient;
        this.config = config;
    }

    @Override
    public void routing(HttpRules rules) {
        rules.get("/wallet", this::generateWallet);
    }

    /**
     * Generate wallet file for the configured ATP.
     */
    private void generateWallet(ServerRequest req, ServerResponse res) {
        String ocid = config.get("oci.atp.ocid").asString().get();
        GenerateAutonomousDatabaseWalletDetails walletDetails =
                GenerateAutonomousDatabaseWalletDetails.builder()
                        .password(ocid)
                        .build();
        GenerateAutonomousDatabaseWalletResponse walletResponse = databaseClient
                .generateAutonomousDatabaseWallet(
                        GenerateAutonomousDatabaseWalletRequest.builder()
                                .autonomousDatabaseId(ocid)
                                .generateAutonomousDatabaseWalletDetails(walletDetails)
                                .build());

        if (walletResponse.getContentLength() == 0) {
            LOGGER.log(Level.SEVERE, "GenerateAutonomousDatabaseWalletResponse is empty");
            res.status(Status.NOT_FOUND_404).send();
            return;
        }

        byte[] walletContent;
        try {
            walletContent = walletResponse.getInputStream().readAllBytes();
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error processing GenerateAutonomousDatabaseWalletResponse", e);
            res.status(Status.INTERNAL_SERVER_ERROR_500).send();
            return;
        }

        DbClient dbClient = createDbClient(walletContent);
        Optional<DbRow> row = dbClient.execute().query("SELECT 'Hello world!!' FROM DUAL").findFirst();
        if (row.isPresent()) {
            res.send(row.get().column(1).as(String.class));
        } else {
            res.status(404).send();
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
        return new JdbcClientProvider().builder()
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
            ZipEntry entry;
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
     * Returns JDBC URL with connection description for the given service based on {@code tnsnames.ora} in wallet.
     *
     * @param walletContent     walletContent
     * @param tnsNetServiceName tnsNetServiceName
     * @return String
     */
    private static String getJdbcUrl(byte[] walletContent, String tnsNetServiceName) throws IllegalStateException {
        String jdbcUrl = null;
        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new ByteArrayInputStream(walletContent)))) {
            ZipEntry entry;
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
