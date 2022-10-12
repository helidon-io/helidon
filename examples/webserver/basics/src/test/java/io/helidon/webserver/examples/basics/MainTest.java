/*
 * Copyright (c) 2017, 2022 Oracle and/or its affiliates.
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

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class MainTest {

    @Test
    public void firstRouting() throws Exception {
        // POST
        TestResponse response = createClient(Main::firstRouting).path("/post-endpoint").post();
        assertThat(response.status().code(), is(201));
        // GET
        response = createClient(Main::firstRouting).path("/get-endpoint").get();
        assertThat(response.status().code(), is(204));
        assertThat(response.asString().get(), is("Hello World!"));
    }

    @Test
    public void routingAsFilter() throws Exception {
        // POST
        TestResponse response = createClient(Main::routingAsFilter).path("/post-endpoint").post();
        assertThat(response.status().code(), is(201));
        // GET
        response = createClient(Main::routingAsFilter).path("/get-endpoint").get();
        assertThat(response.status().code(), is(204));
    }

    @Test
    public void parametersAndHeaders() throws Exception {
        TestResponse response = createClient(Main::parametersAndHeaders).path("/context/aaa")
                                                                       .queryParameter("bar", "bbb")
                                                                       .header("foo", "ccc")
                                                                       .get();
        assertThat(response.status().code(), is(200));
        String s = response.asString().get();
        assertThat(s, containsString("id: aaa"));
        assertThat(s, containsString("bar: bbb"));
        assertThat(s, containsString("foo: ccc"));
    }

    @Test
    public void organiseCode() throws Exception {
        // List
        TestResponse response = createClient(Main::organiseCode).path("/catalog-context-path").get();
        assertThat(response.status().code(), is(200));
        assertThat(response.asString().get(), is("1, 2, 3, 4, 5"));
        // Get by id
        response = createClient(Main::organiseCode).path("/catalog-context-path/aaa").get();
        assertThat(response.status().code(), is(200));
        assertThat(response.asString().get(), is("Item: aaa"));
    }

    @Test
    public void readContentEntity() throws Exception {
        // foo
        TestResponse response
                = createClient(Main::readContentEntity).path("/foo")
                                                       .post(MediaPublisher.create(MediaType.TEXT_PLAIN, "aaa"));
        assertThat(response.status().code(), is(200));
        assertThat(response.asString().get(), is("aaa"));
        // bar
        response = createClient(Main::readContentEntity).path("/bar")
                                                        .post(MediaPublisher.create(MediaType.TEXT_PLAIN, "aaa"));
        assertThat(response.status().code(), is(200));
        assertThat(response.asString().get(), is("aaa"));
    }

    @Test
    public void mediaReader() throws Exception {
        TestResponse response = createClient(Main::mediaReader)
                .path("/create-record")
                .post(MediaPublisher.create(MediaType.parse("application/name"), "John Smith"));
        assertThat(response.status().code(), is(201));
        assertThat(response.asString().get(), is("John Smith"));
        // Unsupported Content-Type
        response = createClient(Main::mediaReader)
                .path("/create-record")
                .post(MediaPublisher.create(MediaType.TEXT_PLAIN, "John Smith"));
        assertThat(response.status().code(), is(500));
    }

    @Test
    public void supports() throws Exception {
        // Jersey
        TestResponse response = createClient(Main::supports).path("/api/hw").get();
        assertThat(response.status().code(), is(200));
        assertThat(response.asString().get(), is("Hello world!"));
        // Static content
        response = createClient(Main::supports).path("/index.html").get();
        assertThat(response.status().code(), is(200));
        assertThat(response.headers().first(Http.Header.CONTENT_TYPE).orElse(null), is(MediaType.TEXT_HTML.toString()));
        // JSON
        response = createClient(Main::supports).path("/hello/Europe").get();
        assertThat(response.status().code(), is(200));
        assertThat(response.asString().get(), is("{\"message\":\"Hello Europe\"}"));
    }

    @Test
    public void errorHandling() throws Exception {
        // Valid
        TestResponse response = createClient(Main::errorHandling)
                .path("/compute")
                .post(MediaPublisher.create(MediaType.TEXT_PLAIN, "2"));
        assertThat(response.status().code(), is(200));
        assertThat(response.asString().get(), is("100 / 2 = 50"));
        // Zero
        response = createClient(Main::errorHandling)
                .path("/compute")
                .post(MediaPublisher.create(MediaType.TEXT_PLAIN, "0"));
        assertThat(response.status().code(), is(412));
        // NaN
        response = createClient(Main::errorHandling)
                .path("/compute")
                .post(MediaPublisher.create(MediaType.TEXT_PLAIN, "aaa"));
        assertThat(response.status().code(), is(400));
    }

    private TestClient createClient(Consumer<Main> callTestedMethod) {
        TMain tm = new TMain();
        callTestedMethod.accept(tm);
        assertThat(tm.routing, notNullValue());
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
