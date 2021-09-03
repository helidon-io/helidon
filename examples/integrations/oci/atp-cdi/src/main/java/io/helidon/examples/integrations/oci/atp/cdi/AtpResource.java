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

import java.io.ByteArrayInputStream;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.inject.Inject;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;

import io.helidon.common.http.Http;
import io.helidon.integrations.common.rest.ApiOptionalResponse;
import io.helidon.integrations.oci.atp.OciAutonomousDb;
import io.helidon.integrations.oci.atp.GenerateAutonomousDatabaseWallet;

import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * JAX-RS resource - REST API for the atp example.
 */
@Path("/atp")
public class AtpResource {
    private static final Logger LOGGER = Logger.getLogger(AtpResource.class.getName());

    private final OciAutonomousDb autonomousDb;

    @Inject
    AtpResource(OciAutonomousDb autonomousDb) {
        this.autonomousDb = autonomousDb;
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
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        GenerateAutonomousDatabaseWallet.Response response = entity.get();

        try {
            LOGGER.log(Level.INFO, "Wallet Content Length: " + response.walletArchive().getContent().length);
            ZipInputStream zipStream = new ZipInputStream(new ByteArrayInputStream(response.walletArchive().getContent()));
            ZipEntry entry = null;
            while ((entry = zipStream.getNextEntry()) != null) {
                String entryName = entry.getName();
                LOGGER.log(Level.INFO, "Wallet FileEntry:" + entryName);
                //FileOutputStream out = new FileOutputStream(entryName);
                //byte[] byteBuff = new byte[4096];
                //int bytesRead = 0;
                //while ((bytesRead = zipStream.read(byteBuff)) != -1) {
                //    out.write(byteBuff, 0, bytesRead);
                //}
                //out.close();
                zipStream.closeEntry();
            }
            zipStream.close();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Exception while processing wallet content", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }

        return Response.status(Response.Status.OK).build();
    }
}

