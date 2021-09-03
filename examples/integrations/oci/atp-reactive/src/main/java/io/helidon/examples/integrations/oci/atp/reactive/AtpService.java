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

import java.io.ByteArrayInputStream;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import io.helidon.common.http.DataChunk;
import io.helidon.common.http.Http;
import io.helidon.integrations.oci.atp.OciAutonomousDbRx;
import io.helidon.integrations.oci.atp.GenerateAutonomousDatabaseWallet;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import io.helidon.webserver.Service;

class AtpService implements Service {
    private static final Logger LOGGER = Logger.getLogger(AtpService.class.getName());

    private final OciAutonomousDbRx autonomousDbRx;

    AtpService(OciAutonomousDbRx autonomousDbRx) {
        this.autonomousDbRx = autonomousDbRx;
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
                .forSingle(apiResponse -> {
                    Optional<GenerateAutonomousDatabaseWallet.Response> entity = apiResponse.entity();
                    if (entity.isEmpty()) {
                        res.status(Http.Status.NOT_FOUND_404).send();
                    } else {
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
                            res.status(Http.Status.INTERNAL_SERVER_ERROR_500).send();
                        }
                        res.status(Http.Status.OK_200).send();
                    }
                })
                .exceptionally(res::send);
    }
}
