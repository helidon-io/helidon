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

package io.helidon.tests.integration.webserver.gh2631;

import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

import io.helidon.webserver.Routing;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.staticcontent.StaticContentSupport;

public class Gh2631 {
    public static void main(String[] args) {
        startServer();
    }

    static WebServer startServer() {
        return WebServer.builder()
                .routing(routing())
                .build()
                .start()
                .await(10, TimeUnit.SECONDS);
    }

    private static Routing routing() {
        StaticContentSupport classpath = StaticContentSupport.builder("web")
                .welcomeFileName("index.txt")
                .build();
        StaticContentSupport file = StaticContentSupport.builder(Paths.get("src/main/resources/web"))
                .welcomeFileName("index.txt")
                .build();

        return Routing.builder()
                .register("/simple", classpath)
                .register("/fallback", classpath)
                .register("/fallback", StaticContentSupport.singleFile("fallback/index.txt"))
                .register("/simpleFile", file)
                .register("/fallbackFile", file)
                .register("/fallbackFile", StaticContentSupport.singleFile(Paths.get("src/main/resources/fallback/index.txt")))
                .build();
    }
}
