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

package io.helidon.examples.integrations.oci.atp.reactive;

import java.sql.SQLException;
import java.util.logging.Logger;
import java.util.logging.Level;

import io.helidon.common.reactive.Multi;
import io.helidon.config.Config;
import io.helidon.dbclient.DbClient;
import io.helidon.dbclient.jdbc.JdbcDbClientProvider;
import io.helidon.integrations.common.rest.ApiOptionalResponse;
import io.helidon.integrations.oci.atp.GenerateAutonomousDatabaseWallet;
import io.helidon.integrations.oci.atp.OciAutonomousDbRx;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import io.helidon.webserver.Service;

import oracle.jdbc.pool.OracleDataSource;
import oracle.ucp.jdbc.PoolDataSource;
import oracle.ucp.jdbc.PoolDataSourceFactory;

class AtpService implements Service {
    private static final Logger LOGGER = Logger.getLogger(AtpService.class.getName());

    private final OciAutonomousDbRx autonomousDbRx;
    private final Config config;

    AtpService(OciAutonomousDbRx autonomousDbRx, Config config) {
        this.autonomousDbRx = autonomousDbRx;
        this.config = config;
    }

    @Override
    public void update(Routing.Rules rules) {
        rules.get("/wallet", this::generateWallet);
    }

    /**
     * Generate wallet file for the configured ATP.
     */
    private void generateWallet(ServerRequest req, ServerResponse res) {
        autonomousDbRx.generateWallet(GenerateAutonomousDatabaseWallet.Request.builder())
                .map(ApiOptionalResponse::entity)
                .map(e -> e.map(GenerateAutonomousDatabaseWallet.Response::walletArchive))
                .flatMap(maybeArchive ->
                        maybeArchive.map(archive -> {
                                    try {
                                        return createDbClient(archive).execute(exec -> exec.query("SELECT 'Hello world!!' FROM DUAL"));
                                    } catch (SQLException sqlException) {
                                        LOGGER.log(Level.SEVERE, "Error creating DbClient", sqlException);
                                    }
                                    res.status(500).send();
                                    return null;
                                }
                        ).orElseGet(() -> {
                            res.status(404).send();
                            return Multi.empty();
                        })
                )
                .first()
                .map(dbRow -> dbRow.column(1).as(String.class))
                .onError(res::send)
                .forSingle(res::send);
    }

    DbClient createDbClient(GenerateAutonomousDatabaseWallet.WalletArchive walletArchive) throws SQLException {
        PoolDataSource pds = PoolDataSourceFactory.getPoolDataSource();
        try {
            pds.setSSLContext(walletArchive.getSSLContext());
            pds.setURL(walletArchive.getJdbcUrl(config.get("db.serviceName")
                    .as(String.class)
                    .orElseThrow(() -> new IllegalStateException("Missing serviceName!!"))));
            pds.setUser(config.get("db.userName").as(String.class).orElse("ADMIN"));
            pds.setPassword(config.get("db.password")
                    .as(String.class)
                    .orElseThrow(() -> new IllegalStateException("Missing password!!")));
            pds.setConnectionFactoryClassName(OracleDataSource.class.getName());
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error setting up PoolDataSource", e);
            throw e;
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
}
