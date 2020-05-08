/*
 * Copyright (c) 2017, 2020 Oracle and/or its affiliates.
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

package io.helidon.webserver.examples.basics;

import java.util.function.Consumer;

import io.helidon.common.http.Http;
import io.helidon.common.http.MediaType;
import io.helidon.media.common.MediaContext;
import io.helidon.webserver.Routing;
import io.helidon.webserver.testsupport.MediaPublisher;
import io.helidon.webserver.testsupport.TestClient;
import io.helidon.webserver.testsupport.TestResponse;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MainTest {

    @Test
    public void firstRouting() throws Exception {
        // POST
        TestResponse response = createClient(Main::firstRouting).path("/post-endpoint").post();
        assertEquals(201, response.status().code());
        // GET
        response = createClient(Main::firstRouting).path("/get-endpoint").get();
        assertEquals(204, response.status().code());
        assertEquals("Hello World!", response.asString().get());
    }

    @Test
    public void routingAsFilter() throws Exception {
        // POST
        TestResponse response = createClient(Main::routingAsFilter).path("/post-endpoint").post();
        assertEquals(201, response.status().code());
        // GET
        response = createClient(Main::routingAsFilter).path("/get-endpoint").get();
        assertEquals(204, response.status().code());
    }

    @Test
    public void parametersAndHeaders() throws Exception {
        TestResponse response = createClient(Main::parametersAndHeaders).path("/context/aaa")
                                                                       .queryParameter("bar", "bbb")
                                                                       .header("foo", "ccc")
                                                                       .get();
        assertEquals(200, response.status().code());
        String s = response.asString().get();
        assertTrue(s.contains("id: aaa"));
        assertTrue(s.contains("bar: bbb"));
        assertTrue(s.contains("foo: ccc"));
    }

    @Test
    public void organiseCode() throws Exception {
        // List
        TestResponse response = createClient(Main::organiseCode).path("/catalog-context-path").get();
        assertEquals(200, response.status().code());
        assertEquals("1, 2, 3, 4, 5", response.asString().get());
        // Get by id
        response = createClient(Main::organiseCode).path("/catalog-context-path/aaa").get();
        assertEquals(200, response.status().code());
        assertEquals("Item: aaa", response.asString().get());
    }

    @Test
    public void readContentEntity() throws Exception {
        // foo
        TestResponse response
                = createClient(Main::readContentEntity).path("/foo")
                                                       .post(MediaPublisher.create(MediaType.TEXT_PLAIN, "aaa"));
        assertEquals(200, response.status().code());
        assertEquals("aaa", response.asString().get());
        // bar
        response = createClient(Main::readContentEntity).path("/bar")
                                                        .post(MediaPublisher.create(MediaType.TEXT_PLAIN, "aaa"));
        assertEquals(200, response.status().code());
        assertEquals("aaa", response.asString().get());
    }

    @Test
    public void filterAndProcessEntity() throws Exception {
        TestResponse response = createClient(Main::filterAndProcessEntity)
                .path("/create-record")
                .post(MediaPublisher.create(MediaType.parse("application/name"), "John Smith"));
        assertEquals(201, response.status().code());
        assertEquals("John Smith", response.asString().get());
        // Unsupported Content-Type
        response = createClient(Main::filterAndProcessEntity)
                .path("/create-record")
                .post(MediaPublisher.create(MediaType.TEXT_PLAIN, "John Smith"));
        assertEquals(500, response.status().code());
    }

    @Test
    public void supports() throws Exception {
        // Jersey
        TestResponse response = createClient(Main::supports).path("/api/hw").get();
        assertEquals(200, response.status().code());
        assertEquals("Hello world!", response.asString().get());
        // Static content
        response = createClient(Main::supports).path("/index.html").get();
        assertEquals(200, response.status().code());
        assertEquals(MediaType.TEXT_HTML.toString(), response.headers().first(Http.Header.CONTENT_TYPE).orElse(null));
        // JSON
        response = createClient(Main::supports).path("/hello/Europe").get();
        assertEquals(200, response.status().code());
        assertEquals("{\"message\":\"Hello Europe\"}", response.asString().get());
    }

    @Test
    public void errorHandling() throws Exception {
        // Valid
        TestResponse response = createClient(Main::errorHandling)
                .path("/compute")
                .post(MediaPublisher.create(MediaType.TEXT_PLAIN, "2"));
        assertEquals(200, response.status().code());
        assertEquals("100 / 2 = 50", response.asString().get());
        // Zero
        response = createClient(Main::errorHandling)
                .path("/compute")
                .post(MediaPublisher.create(MediaType.TEXT_PLAIN, "0"));
        assertEquals(412, response.status().code());
        // NaN
        response = createClient(Main::errorHandling)
                .path("/compute")
                .post(MediaPublisher.create(MediaType.TEXT_PLAIN, "aaa"));
        assertEquals(400, response.status().code());
    }

    private TestClient createClient(Consumer<Main> callTestedMethod) {
        TMain tm = new TMain();
        callTestedMethod.accept(tm);
        assertNotNull(tm.routing);
        return TestClient.create(tm.routing, tm.mediaContext);
    }

    static class TMain extends Main {

        private Routing routing;
        private MediaContext mediaContext;

        @Override
        protected void startServer(Routing routing, MediaContext mediaContext) {
            this.routing = routing;
            this.mediaContext = mediaContext;
        }
    }
}
