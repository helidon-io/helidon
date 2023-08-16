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

package io.helidon.webserver.tests.gh2631;

import java.nio.file.Paths;

import io.helidon.logging.common.LogConfig;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.staticcontent.StaticContentService;

public class Gh2631 {
    public static void main(String[] args) {
        startServer();
    }

    static WebServer startServer() {
        return WebServer.builder()
                .routing(Gh2631::routing)
                .build()
                .start();
    }

    static void routing(HttpRouting.Builder routing) {
        LogConfig.configureRuntime();

        StaticContentService classpath = StaticContentService.builder("web")
                .welcomeFileName("index.txt")
                .build();
        StaticContentService file = StaticContentService.builder(Paths.get("src/main/resources/web"))
                .welcomeFileName("index.txt")
                .build();

        routing.register("/simple", classpath)
                .register("/fallback", classpath)
                .register("/fallback", StaticContentService.builder("fallback")
                        .pathMapper(path -> "index.txt")
                        .build())
                .register("/simpleFile", file)
                .register("/fallbackFile", file)
                .register("/fallbackFile", StaticContentService.builder(Paths.get("src/main/resources/fallback"))
                        .pathMapper(path -> "index.txt")
                        .build());
    }
}
