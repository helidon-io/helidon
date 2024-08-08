/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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
package io.helidon.webserver.tests;

import io.helidon.http.HeaderNames;
import io.helidon.http.Status;
import io.helidon.logging.common.LogConfig;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
class ContentEncodingEmptyTest {

    private final Http1Client client;

    ContentEncodingEmptyTest(Http1Client client) {
        this.client = client;
    }

    @SetUpRoute
    static void routing(HttpRules rules) {
        LogConfig.configureRuntime();
        rules.post("/hello", (req, res) -> {
            res.status(Status.NO_CONTENT_204);
            res.send();     // empty entity
        }).post("/hello_filter", (req, res) -> {
            res.streamFilter(os -> os);     // forces filter codepath
            res.status(Status.NO_CONTENT_204);
            res.send();
        });
    }

    @Test
    void gzipEncodeEmptyEntity() {
        Http1ClientResponse res = client.post("hello")
                .header(HeaderNames.CONTENT_TYPE, "application/json")
                .header(HeaderNames.ACCEPT_ENCODING, "gzip")
                .request();
        assertThat(res.status().code(), is(204));
    }

    @Test
    void gzipEncodeEmptyEntityFilter() {
        Http1ClientResponse res = client.post("hello_filter")
                .header(HeaderNames.CONTENT_TYPE, "application/json")
                .header(HeaderNames.ACCEPT_ENCODING, "gzip")
                .request();
        assertThat(res.status().code(), is(204));
    }
}
