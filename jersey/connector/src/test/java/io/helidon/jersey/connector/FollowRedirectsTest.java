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
import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientResponseContext;
import javax.ws.rs.client.ClientResponseFilter;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import java.io.IOException;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.extension.Extension;
import org.glassfish.jersey.client.ClientProperties;
import org.glassfish.jersey.client.ClientResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;

/**
 * Helidon connector follow redirect tests.
 */
public class FollowRedirectsTest extends AbstractTest {

    private static RedirectResource redirectResource;

    @Path("/test")
    public static class RedirectResource {
        @GET
        public String get() {
            return "GET";
        }

        @GET
        @Path("redirect")
        public Response redirect() {
            return Response.seeOther(UriBuilder.fromResource(RedirectResource.class).build()).build();
        }
    }

    @BeforeAll
    public static void setup() {
        redirectResource =  new RedirectResource();
        UncachedResponseMethodExecutor executor = new UncachedResponseMethodExecutor(redirectResource::redirect);
        AbstractTest.extensions.set(new Extension[] {
                executor,
                new ContentLengthSetter()
        });

        AbstractTest.rules.set(
                () -> {
                    wireMock.stubFor(
                            WireMock.get(WireMock.urlEqualTo("/test/redirect")).willReturn(
                                    WireMock.ok().withTransformers(executor.getName())
                            )
                    );
                    wireMock.stubFor(
                            WireMock.get(WireMock.urlEqualTo("/test")).willReturn(
                                    WireMock.ok(redirectResource.get())
                            )
                    );
                });

        AbstractTest.setup();
    }

    @Override
    protected WebTarget target(String uri, String entityType) {
        WebTarget target = super.target(uri, entityType);
        target.register(RedirectTestFilter.class);
        return target;
    }

    private static class RedirectTestFilter implements ClientResponseFilter {
        public static final String RESOLVED_URI_HEADER = "resolved-uri";

        @Override
        public void filter(ClientRequestContext requestContext, ClientResponseContext responseContext) throws IOException {
            if (responseContext instanceof ClientResponse) {
                ClientResponse clientResponse = (ClientResponse) responseContext;
                responseContext.getHeaders().putSingle(RESOLVED_URI_HEADER, clientResponse.getResolvedRequestUri().toString());
            }
        }
    }

    @ParamTest
    public void testDoFollow(String entityType) {
        Response r = target("test/redirect", entityType).register(RedirectTestFilter.class).request().get();
        Assertions.assertEquals(200, r.getStatus());
        Assertions.assertEquals("GET", r.readEntity(String.class));

        Assertions.assertEquals(
                UriBuilder.fromUri(getBaseUri()).path(RedirectResource.class).build().toString(),
                r.getHeaderString(RedirectTestFilter.RESOLVED_URI_HEADER));
    }

    @ParamTest
    public void testDontFollow(String entityType) {
        WebTarget t = target("test/redirect", entityType);
        t.property(ClientProperties.FOLLOW_REDIRECTS, false);
        Assertions.assertEquals(303, t.request().get().getStatus());
    }
}
