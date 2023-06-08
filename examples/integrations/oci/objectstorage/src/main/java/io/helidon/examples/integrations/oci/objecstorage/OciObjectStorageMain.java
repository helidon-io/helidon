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

package io.helidon.examples.integrations.oci.objecstorage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import io.helidon.config.Config;
import io.helidon.config.spi.ConfigSource;
import io.helidon.nima.webserver.WebServer;
import io.helidon.nima.webserver.http.HttpRouting;

import static io.helidon.config.ConfigSources.classpath;
import static io.helidon.config.ConfigSources.file;

/**
 * Main class of the example.
 * This example sets up a web server to serve REST API to upload/download/delete objects.
 */
public final class OciObjectStorageMain {

    private static Config config;

    private OciObjectStorageMain() {
    }

    /**
     * Main method.
     *
     * @param args ignored
     */
    public static void main(String[] args) {

        config = Config
                .builder()
                .sources(examplesConfig())
                .build();

        WebServer server = WebServer.builder()
                .routing(OciObjectStorageMain::routing)
                .start();
    }

    /**
     * Updates HTTP Routing.
     */
    static void routing(HttpRouting.Builder routing) {
        ObjectStorageService objectStorageService = new ObjectStorageService(config);
        routing.register("/files", objectStorageService);
    }

    private static List<Supplier<? extends ConfigSource>> examplesConfig() {
        List<Supplier<? extends ConfigSource>> suppliers = new ArrayList<>();
        Path path = Paths.get(System.getProperty("user.home") + "/helidon/conf/examples.yaml");
        if (Files.exists(path)) {
            suppliers.add(file(path).build());
        }
        suppliers.add(classpath("application.yaml").build());
        return suppliers;
    }
}
