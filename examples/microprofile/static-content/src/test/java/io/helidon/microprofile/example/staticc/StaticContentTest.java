/*
 * Copyright (c) 2018, 2021 Oracle and/or its affiliates.
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

package io.helidon.microprofile.example.staticc;

import java.io.IOException;

import javax.enterprise.inject.se.SeContainer;
import javax.enterprise.inject.spi.CDI;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import io.helidon.common.http.Http;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertAll;

/**
 * Unit test for {@link HelloWorldResource}.
 */
class StaticContentTest {
    @BeforeAll
    static void initClass() throws IOException {
        Main.main(new String[0]);
    }

    @AfterAll
    static void destroyClass() {
        CDI<Object> current = CDI.current();
        ((SeContainer) current).close();
    }

    @Test
    void testDynamicResource() {
        String response = ClientBuilder.newClient()
                .target("http://localhost:" + Main.getPort() + "/helloworld")
                .request()
                .get(String.class);

        assertAll("Response must be HTML and contain a ref to static resource",
                  () -> assertThat(response, containsString("/resource.html")),
                  () -> assertThat(response, containsString("Hello World"))
        );
    }

    @Test
    void testWelcomePage() {
        try (Response response = ClientBuilder.newClient()
                .target("http://localhost:" + Main.getPort())
                .request()
                .accept(MediaType.TEXT_HTML_TYPE)
                .get()) {
            assertThat("Status should be 200", response.getStatus(), is(Http.Status.OK_200.code()));

            String str = response.readEntity(String.class);

            assertAll(
                    () -> assertThat(response.getMediaType(), is(MediaType.TEXT_HTML_TYPE)),
                    () -> assertThat(str, containsString("server.static.classpath.location=/WEB"))
            );
        }
    }

    @Test
    void testStaticResource() {
        try (Response response = ClientBuilder.newClient()
                .target("http://localhost:" + Main.getPort() + "/resource.html")
                .request()
                .accept(MediaType.TEXT_HTML_TYPE)
                .get()) {
            assertThat("Status should be 200", response.getStatus(), is(Http.Status.OK_200.code()));

            String str = response.readEntity(String.class);

            assertAll(
                    () -> assertThat(response.getMediaType(), is(MediaType.TEXT_HTML_TYPE)),
                    () -> assertThat(str, containsString("server.static.classpath.location=/WEB"))
            );
        }
    }
}
