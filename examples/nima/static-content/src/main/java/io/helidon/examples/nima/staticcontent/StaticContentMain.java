/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.examples.nima.staticcontent;

import io.helidon.nima.webserver.WebServer;
import io.helidon.nima.webserver.http.HttpRouting;
import io.helidon.nima.webserver.staticcontent.StaticContentService;

/**
 * Static content example.
 */
public class StaticContentMain {
    private StaticContentMain() {
    }

    /**
     * Main methods.
     *
     * @param args ignored
     */
    public static void main(String[] args) {
        WebServer.builder()
                .host("127.0.0.1")
                .port(8080)
                .routing(StaticContentMain::routing)
                .start();
        System.out.println("You can access static content on http://localhost:8080/favicon.ico");
        System.out.println("You can access static endpoint on http://localhost:8080/api/greet");
    }

    static void routing(HttpRouting.Builder routing) {
        // register static content on root path of the server
        // use classpath /web to look for resources
        routing.register("/", StaticContentService.builder("web"))
                .get("/api/greet", (req, res) -> res.send("Hello World!"));
    }
}

