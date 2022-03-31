/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.tests.integration.security.mapper;

import javax.enterprise.inject.se.SeContainer;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;

import io.helidon.microprofile.cdi.HelidonContainer;
import io.helidon.microprofile.server.ServerCdiExtension;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class RestrictedResourceTest {
    private static WebTarget target;
    private static HelidonContainer instance;

    @BeforeAll
    static void initClass() {
        instance = HelidonContainer.instance();
        SeContainer container = instance.start();
        Client adminClient = ClientBuilder.newBuilder().build();
        int port = container.getBeanManager().getExtension(ServerCdiExtension.class).port();
        String uri = "http://localhost:" + port;
        target = adminClient.target(uri);
    }

    @AfterAll
    static void destroyClass() {
        instance.shutdown();
    }

    @Test
    void testRestricted() {
        Response response = target.path("restricted")
                .request()
                .get();

        assertThat(response.getStatus(), is(401));
        assertThat(response.getHeaderString("PROVIDER"), is("RestrictedProvider"));
        assertThat(response.getHeaderString("MAPPED-BY"), is("MySecurityResponseMapper"));
    }
}