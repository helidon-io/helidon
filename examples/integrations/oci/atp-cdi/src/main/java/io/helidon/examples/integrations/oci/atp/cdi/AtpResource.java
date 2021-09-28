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

package io.helidon.examples.integrations.oci.atp.cdi;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Objects;
import java.util.Optional;

import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;

import io.helidon.integrations.common.rest.ApiOptionalResponse;
import io.helidon.integrations.oci.atp.OciAutonomousDb;
import io.helidon.integrations.oci.atp.GenerateAutonomousDatabaseWallet;

import oracle.ucp.jdbc.PoolDataSource;

import org.eclipse.microprofile.config.inject.ConfigProperty;
/**
 * JAX-RS resource - REST API for the atp example.
 */
@Path("/atp")
public class AtpResource {
    private static final Logger LOGGER = Logger.getLogger(AtpResource.class.getName());

    private final OciAutonomousDb autonomousDb;
    private final PoolDataSource atpDataSource;
    private final String atpServiceName;

    @Inject
    AtpResource(OciAutonomousDb autonomousDb, @Named("atp") PoolDataSource atpDataSource,
                @ConfigProperty(name = "oracle.ucp.jdbc.PoolDataSource.atp.serviceName") String atpServiceName) {
        this.autonomousDb = autonomousDb;
        this.atpDataSource = Objects.requireNonNull(atpDataSource);
        this.atpServiceName = atpServiceName;
    }

    /**
     * Generate wallet file for the configured ATP.
     *
     * @return response containing wallet file
     */
    @GET
    @Path("/wallet")
    public Response generateWallet() {
        ApiOptionalResponse<GenerateAutonomousDatabaseWallet.Response> ociResponse = autonomousDb.generateWallet(GenerateAutonomousDatabaseWallet.Request.builder());
        Optional<GenerateAutonomousDatabaseWallet.Response> entity = ociResponse.entity();

        if (entity.isEmpty()) {
            LOGGER.log(Level.SEVERE, "GenerateAutonomousDatabaseWallet.Response is empty");
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        GenerateAutonomousDatabaseWallet.Response response = entity.get();
        GenerateAutonomousDatabaseWallet.WalletArchive walletArchive = response.walletArchive();
        String returnEntity = null;
        try {
            this.atpDataSource.setSSLContext(walletArchive.getSSLContext());
            this.atpDataSource.setURL(walletArchive.getJdbcUrl(this.atpServiceName));
            try(Connection connection = this.atpDataSource.getConnection();
            PreparedStatement ps = connection.prepareStatement("SELECT 'Hello world!!' FROM DUAL");
            ResultSet rs = ps.executeQuery()) {
                rs.next();
                returnEntity = rs.getString(1);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error setting up DataSource", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }

        return Response.status(Response.Status.OK).entity(returnEntity).build();
    }
}

