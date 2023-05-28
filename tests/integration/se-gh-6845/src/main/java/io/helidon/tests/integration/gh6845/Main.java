/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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
package io.helidon.tests.integration.gh6845;

import java.util.Arrays;

import io.helidon.common.LogConfig;
import io.helidon.common.http.DataChunk;
import io.helidon.common.reactive.Single;
import io.helidon.media.multipart.ContentDisposition;
import io.helidon.media.multipart.MultiPartSupport;
import io.helidon.media.multipart.WriteableBodyPart;
import io.helidon.media.multipart.WriteableBodyPartHeaders;
import io.helidon.media.multipart.WriteableMultiPart;
import io.helidon.webserver.Routing;
import io.helidon.webserver.WebServer;

import static io.helidon.common.http.MediaType.TEXT_PLAIN;

/**
 * Main class of this integration test.
 */
public final class Main {

    /**
     * Cannot be instantiated.
     */
    private Main() {
    }

    /**
     * Application main entry point.
     *
     * @param args command line arguments.
     */
    public static void main(String[] args) {
        startServer();
    }

    /**
     * Start the server.
     *
     * @return the created {@link io.helidon.webserver.WebServer} instance
     */
    static WebServer startServer() {
        // load logging configuration
        LogConfig.configureRuntime();

        WebServer server = WebServer.builder()
                                    .routing(createRouting())
                                    .addMediaSupport(MultiPartSupport.create())
                                    .printFeatureDetails(true)
                                    .build();

        // Try to start the server. If successful, print some info and arrange to
        // print a message at shutdown. If unsuccessful, print the exception.
        server.start()
              .thenAccept(ws -> {
                  System.out.println(
                          "WEB server is up! http://localhost:" + ws.port() + "/greet");
                  ws.whenShutdown().thenRun(()
                          -> System.out.println("WEB server is DOWN. Good bye!"));
              })
              .exceptionally(t -> {
                  System.err.println("Startup failed: " + t.getMessage());
                  t.printStackTrace(System.err);
                  return null;
              });

        // Server threads are not daemon. No need to block. Just react.
        return server;
    }

    /**
     * Creates new {@link io.helidon.webserver.Routing}.
     *
     * @return routing configured
     */
    private static Routing createRouting() {
        return Routing.builder()
                      .get("/", (req, res) -> {
                          byte[] contents = new byte[21 * 1024 * 1024]; // 21 MiB
                          Arrays.fill(contents, (byte) 0x20); // fill with spaces
                          res.send(WriteableMultiPart
                                  .builder()
                                  .bodyPart(WriteableBodyPart
                                          .builder()
                                          .publisher(Single.just(DataChunk.create(contents)))
                                          .headers(WriteableBodyPartHeaders
                                                  .builder()
                                                  .contentType(TEXT_PLAIN)
                                                  .contentDisposition(ContentDisposition
                                                          .builder()
                                                          .name("too-big")
                                                          .build())
                                                  .build())
                                          .build())
                                  .build());
                      })
                      .build();
    }
}
