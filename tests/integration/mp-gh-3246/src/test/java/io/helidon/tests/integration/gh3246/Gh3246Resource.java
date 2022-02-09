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

package io.helidon.tests.integration.gh3246;

import io.helidon.security.annotations.Authenticated;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.WebTarget;

@Path("/test")
public class Gh3246Resource {
    private Client client;

    @PostConstruct
    public void postConstruct() {
        this.client = ClientBuilder.newClient();
    }
    @PreDestroy
    public void preDestroy() {
        this.client.close();
    }

    @GET
    @Path("/hello")
    public String hello() {
        return "hello";
    }

    @GET
    @Path("/callout")
    public String callout(@QueryParam("port") int port) {
        WebTarget webTarget = client.target("http://localhost:" + port + "/test/hello");

        return webTarget.request()
                .get(String.class);
    }

    @GET
    @Path("/secured")
    @Authenticated
    public String secured(@QueryParam("port") int port) {
        WebTarget webTarget = client.target("http://localhost:" + port + "/test/hello");

        return webTarget.request()
                .get(String.class);
    }
}
