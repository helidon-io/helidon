/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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
package io.helidon.docs.mp.guides;

import io.helidon.config.Config;
import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.media.jsonp.JsonpSupport;
import io.helidon.microprofile.server.RoutingPath;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.Handler;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.HttpService;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.Produces;

@SuppressWarnings("ALL")
class SeServicesSnippets {

    // tag::snippet_1[]
    public class CoolingService implements HttpService, Handler {

        public static final HeaderName COOL_HEADER_NAME = HeaderNames.create("Cool-Header");
        public static final String COOLING_VALUE = "This is way cooler response than ";

        @Override
        public void routing(HttpRules rules) {
            rules.any(this);
        }

        @Override
        public void handle(ServerRequest req, ServerResponse res) {
            res.headers().add(COOL_HEADER_NAME, COOLING_VALUE);
            res.next();
        }
    }
    // end::snippet_1[]

    void snippet_2(Config config) {
        // tag::snippet_2[]
        WebServer.builder()
                .routing(it -> it
                        .register("/cool", new CoolingService())) // <1>
                .config(config)
                .mediaContext(it -> it
                        .addMediaSupport(JsonpSupport.create()))
                .build()
                .start();
        // end::snippet_2[]
    }

    class Snippet3 {

        // tag::snippet_3[]
        @ApplicationScoped
        public class MyBean {

            @Produces
            @ApplicationScoped
            @RoutingPath("/cool")
            public HttpService coolService() {
                return new CoolingService();
            }

        }
        // end::snippet_3[]
    }

}
