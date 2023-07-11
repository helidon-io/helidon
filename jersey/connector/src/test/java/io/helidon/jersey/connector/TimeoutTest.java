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

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.extension.Extension;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.core.Response;
import org.glassfish.jersey.client.ClientProperties;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

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

        Extension[] extensions = new Extension[] {
                sleepExecutor,
                new ContentLengthSetter()
        };
        Rules rules = () -> {
            wireMockServer.stubFor(
                    WireMock.get(WireMock.urlEqualTo("/test")).willReturn(
                            WireMock.ok(timeoutResource.get())
                    )
            );
            wireMockServer.stubFor(
                    WireMock.get(WireMock.urlEqualTo("/test/timeout")).willReturn(
                            WireMock.ok().withTransformers(sleepExecutor.getName())
                    )
            );
        };
        setup(rules, extensions);
    }

    @Test
    public void testFast() {
        Response r = target("test").request().get();
        assertThat(r.getStatus(), is(200));
        assertThat(r.readEntity(String.class), is("GET"));
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
