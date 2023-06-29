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

import java.io.IOException;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.extension.Extension;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientResponseContext;
import jakarta.ws.rs.client.ClientResponseFilter;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import org.glassfish.jersey.client.ClientProperties;
import org.glassfish.jersey.client.ClientResponse;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

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
        redirectResource = new RedirectResource();
        UncachedResponseMethodExecutor executor = new UncachedResponseMethodExecutor(redirectResource::redirect);
        Extension[] extensions = new Extension[]{
                executor,
                new ContentLengthSetter()
        };

        Rules rules = () -> {
            wireMockServer.stubFor(
                    WireMock.get(WireMock.urlEqualTo("/test/redirect")).willReturn(
                            WireMock.ok().withTransformers(executor.getName())
                    )
            );
            wireMockServer.stubFor(
                    WireMock.get(WireMock.urlEqualTo("/test")).willReturn(
                            WireMock.ok(redirectResource.get())
                    )
            );
        };
        setup(rules, extensions);
    }

    @Override
    protected WebTarget target(String uri) {
        WebTarget target = super.target(uri);
        target.register(RedirectTestFilter.class);
        return target;
    }

    private static class RedirectTestFilter implements ClientResponseFilter {
        public static final String RESOLVED_URI_HEADER = "resolved-uri";

        @Override
        public void filter(ClientRequestContext requestContext, ClientResponseContext responseContext) throws IOException {
            if (responseContext instanceof ClientResponse clientResponse) {
                responseContext.getHeaders().putSingle(RESOLVED_URI_HEADER,
                        clientResponse.getResolvedRequestUri().toString());
            }
        }
    }

    @Test
    public void testDoFollow() {
        Response r = target("test/redirect").register(RedirectTestFilter.class).request().get();
        assertThat(r.getStatus(), is(200));
        assertThat(r.readEntity(String.class), is("GET"));

        assertThat(r.getHeaderString(RedirectTestFilter.RESOLVED_URI_HEADER),
                is(UriBuilder.fromUri(getBaseUri()).path(RedirectResource.class).build().toString()));
    }

    @Test
    public void testDontFollow() {
        WebTarget t = target("test/redirect");
        t.property(ClientProperties.FOLLOW_REDIRECTS, false);
        assertThat(t.request().get().getStatus(), is(303));
    }
}
