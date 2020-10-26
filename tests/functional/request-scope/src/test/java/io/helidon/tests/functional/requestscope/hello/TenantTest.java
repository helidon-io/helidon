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

package io.helidon.tests.functional.requestscope.hello;

import java.io.IOException;
import java.util.logging.LogManager;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;

import io.helidon.microprofile.server.Server;

import io.netty.handler.codec.http.HttpResponseStatus;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class TenantTest {
    private static Server server;
    private static WebTarget baseTarget;

    @BeforeAll
    static void initClass() throws IOException {
        LogManager.getLogManager().readConfiguration(TenantTest.class.getResourceAsStream("/logging.properties"));
        Main.main(new String[0]);
        server = Main.server();
        Client client = ClientBuilder.newClient();
        baseTarget = client.target("http://localhost:" + server.port());
    }

    @AfterAll
    static void destroyClass() {
        server.stop();
    }

    @Test
    public void test() {
        Response r = baseTarget.path("test")
                .request()
                .header("x-tenant-id", "some-tenant-id")
                .get();
        assertThat(r.getStatus(), is(HttpResponseStatus.OK.code()));
    }
}