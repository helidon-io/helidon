/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

package io.helidon.security.examples.webserver.basic;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import io.helidon.common.LogConfig;
import io.helidon.common.http.MediaType;
import io.helidon.config.Config;
import io.helidon.security.SecurityContext;
import io.helidon.security.integration.webserver.WebSecurity;
import io.helidon.webserver.Routing;
import io.helidon.webserver.StaticContentSupport;
import io.helidon.webserver.WebServer;

public class BasicExampleConfigMain {
    public static void main(String[] args) {
        BasicExampleUtil.startAndPrintEndpoints(BasicExampleConfigMain::startServer);
    }

    static WebServer startServer() {
        LogConfig.initClass();

        Config config = Config.create();

        Routing routing = Routing.builder()
                // must be configured first, to protect endpoints
                .register(WebSecurity.create(config.get("security")))
                .register("/static", StaticContentSupport.create("/WEB"))
                .get("/{*}", (req, res) -> {
                    Optional<SecurityContext> securityContext = req.context().get(SecurityContext.class);
                    res.headers().contentType(MediaType.TEXT_PLAIN.withCharset("UTF-8"));
                    res.send("Hello, you are: \n" + securityContext
                            .map(ctx -> ctx.user().orElse(SecurityContext.ANONYMOUS).toString())
                            .orElse("Security context is null"));
                })
                .build();

        return WebServer.builder()
                .config(config.get("server"))
                .routing(routing)
                .build()
                .start()
                .await(10, TimeUnit.SECONDS);

    }
}
