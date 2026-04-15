/*
 * Copyright (c) 2021, 2026 Oracle and/or its affiliates.
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
import io.helidon.webserver.http.HttpService;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.staticcontent.ClasspathHandlerConfig;
import io.helidon.webserver.staticcontent.FileSystemHandlerConfig;
import io.helidon.webserver.staticcontent.StaticContentFeature;

public class Gh2631 {

    static void routing(HttpRouting.Builder routing) {
        LogConfig.configureRuntime();

        HttpService classpath = StaticContentFeature.createService(
                ClasspathHandlerConfig.builder()
                        .location("web")
                        .welcome("index.txt")
                        .build());
        HttpService file = StaticContentFeature.createService(
                FileSystemHandlerConfig.builder()
                        .location(Paths.get("src/main/resources/web"))
                        .welcome("index.txt")
                        .build());

        routing.register("/simple", classpath)
                .register("/fallback", classpath)
                .register("/fallback", StaticContentFeature.createService(
                        ClasspathHandlerConfig.builder()
                                .location("fallback")
                                .pathMapper(path -> "index.txt")
                                .build()))
                .register("/simpleFile", file)
                .register("/fallbackFile", file)
                .register("/fallbackFile", StaticContentFeature.createService(
                        FileSystemHandlerConfig.builder()
                                .location(Paths.get("src/main/resources/fallback"))
                                .pathMapper(path -> "index.txt")
                                .build()));
    }
}
