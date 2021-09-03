/*
 * Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
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

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.core.Response;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.extension.Extension;
import org.glassfish.jersey.client.ClientConfig;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;


public class AsyncRequestTest extends AbstractTest {

    private static AsyncResource asyncResource = new AsyncResource();

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
        final UncachedStringMethodExecutor executor = new UncachedStringMethodExecutor(asyncResource::longGet);

        AbstractTest.extensions.set(new Extension[] {
                executor,
                new ContentLengthSetter()
        });

        AbstractTest.rules.set(
                () -> {
                    wireMock.stubFor(
                            WireMock.get(WireMock.urlEqualTo("/async/reset")).willReturn(
                                    WireMock.ok(asyncResource.reset()).withStatus(204)
                            )
                    );
                    wireMock.stubFor(
                            WireMock.get(WireMock.urlEqualTo("/async/short")).willReturn(
                                    WireMock.ok(asyncResource.shortGet())
                            )
                    );
                    wireMock.stubFor(
                            WireMock.get(WireMock.urlEqualTo("/async/long")).willReturn(
                                    WireMock.ok().withTransformers(executor.getName())
                            )
                    );
                });

        AbstractTest.setup();
    }

    @ParamTest
    public void testTwoClientsAsync(String entityType) throws ExecutionException, InterruptedException {
        try (Response resetResponse = target("async", entityType).path("reset").request().get()) {
            Assertions.assertEquals(204, resetResponse.getStatus());
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
            Assertions.assertEquals(200, shortResponse.getStatus());
            Assertions.assertEquals("short", shortResponse.readEntity(String.class));
        }

        try (Response longResponse = futureLongResponse.get()) {
            Assertions.assertEquals(200, longResponse.getStatus());
            Assertions.assertEquals("long", longResponse.readEntity(String.class));
        }

        Assertions.assertEquals(0, asyncResource.shortLong.getCount());
    }
}
