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
import javax.ws.rs.ProcessingException;
import javax.ws.rs.core.Response;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.extension.Extension;
import org.glassfish.jersey.client.ClientProperties;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class TimeoutTest extends AbstractTest {
    private static TimeoutResource timeoutResource;

    @Path("/test")
    public static class TimeoutResource {
        @GET
        public String get() {
            return "GET";
        }

        @GET
        @Path("timeout")
        public String getTimeout() {
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return "GET";
        }
    }

    @BeforeAll
    public static void setup() {
        timeoutResource = new TimeoutResource();
        UncachedStringMethodExecutor sleepExecutor = new UncachedStringMethodExecutor(timeoutResource::getTimeout);

        AbstractTest.extensions.set(new Extension[] {
                sleepExecutor,
                new ContentLengthSetter()
        });

        AbstractTest.rules.set(
                () -> {
                    wireMock.stubFor(
                            WireMock.get(WireMock.urlEqualTo("/test")).willReturn(
                                    WireMock.ok(timeoutResource.get())
                            )
                    );
                    wireMock.stubFor(
                            WireMock.get(WireMock.urlEqualTo("/test/timeout")).willReturn(
                                    WireMock.ok().withTransformers(sleepExecutor.getName())
                            )
                    );
                });

        AbstractTest.setup();
    }

    @Test
    public void testFast() {
        Response r = target("test").request().get();
        Assertions.assertEquals(200, r.getStatus());
        Assertions.assertEquals("GET", r.readEntity(String.class));
    }

    @Test
    public void testSlow() {
        try {
            target("test/timeout").property(ClientProperties.READ_TIMEOUT, 1_000).request().get();
            Assertions.fail("Timeout expected.");
        } catch (ProcessingException e) {
            assertTimeoutException(e);
        }
    }

    @Test
    public void testTimeoutInRequest() {
        try {
            target("test/timeout").request().property(ClientProperties.READ_TIMEOUT, 1_000).get();
            Assertions.fail("Timeout expected.");
        } catch (ProcessingException e) {
            assertTimeoutException(e);
        }
    }

    private void assertTimeoutException(Exception e) {
        String exceptionName = "TimeoutException"; // check netty or JDK TimeoutException
        Throwable t = e.getCause();
        while (t != null) {
            if (t.getClass().getSimpleName().contains(exceptionName)) {
                break;
            }
            t = t.getCause();
        }
        if (t == null) {
            if (e.getCause() != null) {
                if (e.getCause().getCause() != null) {
                    Assertions.fail("Unexpected processing exception cause" + e.getCause().getCause().getMessage());
                } else {
                    Assertions.fail("Unexpected processing exception cause" + e.getCause().getMessage());
                }
            } else {
                Assertions.fail("Unexpected processing exception cause" + e.getMessage());
            }
        }
    }
}
