/*
 * Copyright (c) 2020, 2023 Oracle and/or its affiliates.
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

package io.helidon.jersey.connector;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.extension.Extension;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.core.Response;
import org.glassfish.jersey.client.ClientConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class AsyncRequestTest extends AbstractTest {

    private static final AsyncResource asyncResource = new AsyncResource();

    @Path("async")
    public static class AsyncResource {
        private CountDownLatch shortLong = null;

        @GET
        @Path("reset")
        public String reset() {
            shortLong = new CountDownLatch(1);
            return null;
        }

        @Path("long")
        @GET
        public String longGet() {
            try {
                shortLong.await(10000, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            return shortLong.getCount() == 0 ? "long" : "shortLong CountDownLatch has not been hit";
        }

        @Path("short")
        @GET
        public String shortGet() {
            shortLong.countDown();
            return "short";
        }
    }

    @BeforeAll
    public static void setup() {
        UncachedStringMethodExecutor executor = new UncachedStringMethodExecutor(asyncResource::longGet);

        Extension[] extensions = new Extension[]{
                executor,
                new ContentLengthSetter()
        };
        Rules rules = () -> {
                    wireMockServer.stubFor(
                            WireMock.get(WireMock.urlEqualTo("/async/reset")).willReturn(
                                    WireMock.ok(asyncResource.reset()).withStatus(204)
                            )
                    );
                    wireMockServer.stubFor(
                            WireMock.get(WireMock.urlEqualTo("/async/short")).willReturn(
                                    WireMock.ok(asyncResource.shortGet())
                            )
                    );
                    wireMockServer.stubFor(
                            WireMock.get(WireMock.urlEqualTo("/async/long")).willReturn(
                                    WireMock.ok().withTransformers(executor.getName())
                            )
                    );
                };
        setup(rules, extensions);
    }

    @Test
    public void testTwoClientsAsync() throws ExecutionException, InterruptedException {
        try (Response resetResponse = target("async").path("reset").request().get()) {
            assertThat(resetResponse.getStatus(), is(204));
        }

        ClientConfig config = new ClientConfig();
        config.connectorProvider(new HelidonConnectorProvider());

        Client longClient = ClientBuilder.newClient(config);
        Invocation.Builder longRequest = longClient.target(getBaseUri()).path("async/long").request();

        Client shortClient = ClientBuilder.newClient(config);
        Invocation.Builder shortRequest = shortClient.target(getBaseUri()).path("async/short").request();

        Future<Response> futureLongResponse = longRequest.async().get();
        Future<Response> futureShortResponse = shortRequest.async().get();

        try (Response shortResponse = futureShortResponse.get()) {
            assertThat(shortResponse.getStatus(), is(200));
            assertThat(shortResponse.readEntity(String.class), is("short"));
        }

        try (Response longResponse = futureLongResponse.get()) {
            assertThat(longResponse.getStatus(), is(200));
            assertThat(longResponse.readEntity(String.class), is("long"));
        }

        assertThat(asyncResource.shortLong.getCount(), is(0L));
    }
}
