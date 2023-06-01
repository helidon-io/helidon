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
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import com.oracle.bmc.ConfigFileReader;
import com.oracle.bmc.auth.AuthenticationDetailsProvider;
import com.oracle.bmc.auth.ConfigFileAuthenticationDetailsProvider;
import com.oracle.bmc.database.Database;
import com.oracle.bmc.database.DatabaseClient;
import com.oracle.bmc.database.model.GenerateAutonomousDatabaseWalletDetails;
import com.oracle.bmc.database.requests.GenerateAutonomousDatabaseWalletRequest;
import com.oracle.bmc.database.responses.GenerateAutonomousDatabaseWalletResponse;
import com.oracle.bmc.http.client.Options;

import io.helidon.common.http.Http;
import io.helidon.config.Config;
import io.helidon.config.ConfigException;
import io.helidon.nima.webserver.http.HttpRules;
import io.helidon.nima.webserver.http.HttpService;
import io.helidon.nima.webserver.http.ServerRequest;
import io.helidon.nima.webserver.http.ServerResponse;

import oracle.jdbc.pool.OracleDataSource;
import oracle.security.pki.OraclePKIProvider;
import oracle.ucp.jdbc.PoolDataSource;
import oracle.ucp.jdbc.PoolDataSourceFactory;

/**
 * REST API for the atp example.
 */
public class AtpService implements HttpService {
    private static final Logger LOGGER = Logger.getLogger(AtpService.class.getName());

    private final Database databaseClient;
    private final PoolDataSource atpDataSource;
    private final String atpTnsNetServiceName;

    private final String atpOcid;
    private final String walletPassword;
    private final Config config;

    AtpService(Config config) {
        try {
            this.config = config;
            AuthenticationDetailsProvider authProvider = new ConfigFileAuthenticationDetailsProvider(
                    ConfigFileReader.parseDefault());
            this.databaseClient = DatabaseClient.builder().build(authProvider);
            this.atpTnsNetServiceName = config.get("oracle.ucp.jdbc.PoolDataSource.atp.tnsNetServiceName")
                    .asString()
                    .orElseThrow(() -> new IllegalStateException("Missing tnsNetServiceName!!"));
            this.atpOcid = config.get("oci.atp.ocid")
                    .asString()
                    .orElseThrow(() -> new IllegalStateException("Missing ocid!!"));
            this.walletPassword = config.get("oci.atp.walletPassword")
                    .asString()
                    .orElseThrow(() -> new IllegalStateException("Missing walletPassword!!"));
            atpDataSource = PoolDataSourceFactory.getPoolDataSource();
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
     * Generate wallet file for the configured ATP, make SQL query to the database and return its result in response.
     *
     * @param request  request
     * @param response request
     */
    public void generateWallet(ServerRequest request, ServerResponse response) {
        Options.shouldAutoCloseResponseInputStream(false);
        GenerateAutonomousDatabaseWalletResponse walletResponse =
                databaseClient.generateAutonomousDatabaseWallet(
                        GenerateAutonomousDatabaseWalletRequest.builder()
                                .autonomousDatabaseId(this.atpOcid)
                                .generateAutonomousDatabaseWalletDetails(
                                        GenerateAutonomousDatabaseWalletDetails.builder()
                                                .password(this.walletPassword)
                                                .build())
                                .build());

        if (walletResponse.getContentLength() == 0) {
            LOGGER.log(Level.SEVERE, "GenerateAutonomousDatabaseWalletResponse is empty");
            response.status(Http.Status.NOT_FOUND_404).send();
            return;
        }

        byte[] walletContent = null;
        try {
            walletContent = walletResponse.getInputStream().readAllBytes();
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error processing GenerateAutonomousDatabaseWalletResponse", e);
            response.status(Http.Status.INTERNAL_SERVER_ERROR_500).send();
            return;
        }
        String returnEntity;
        try {
            configureDataSource(walletContent);
            try (
                    Connection connection = this.atpDataSource.getConnection();
                    PreparedStatement ps = connection.prepareStatement("SELECT 'Hello world!!' FROM DUAL");
                    ResultSet rs = ps.executeQuery()
            ) {
                rs.next();
                returnEntity = rs.getString(1);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error setting up DataSource", e);
            response.status(Http.Status.INTERNAL_SERVER_ERROR_500).send();
            return;
        }

        response.status(Http.Status.OK_200);
        response.send(returnEntity);
    }

    private void configureDataSource(byte[] walletContent) throws SQLException {
        atpDataSource.setSSLContext(getSSLContext(walletContent));
        atpDataSource.setURL(getJdbcUrl(walletContent, this.atpTnsNetServiceName));
        atpDataSource.setUser(config.get("atp.db.user")
                .asString()
                .orElseThrow(() -> new IllegalStateException("Missing DB user!!")));
        atpDataSource.setPassword(config.get("atp.db.password")
                .asString()
                .orElseThrow(() -> new IllegalStateException("Missing user password!!")));
        atpDataSource.setConnectionFactoryClassName(OracleDataSource.class.getName());
    }

    /**
     * Returns SSLContext based on cwallet.sso in wallet.
     *
     * @param walletContent walletContent
     * @return SSLContext SSLContext
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
     * @param walletContent walletContent
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

