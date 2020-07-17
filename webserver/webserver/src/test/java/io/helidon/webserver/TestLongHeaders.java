/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

package io.helidon.webserver;

import java.util.concurrent.TimeUnit;

import io.helidon.webclient.WebClient;
import io.helidon.webclient.WebClientRequestBuilder;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class TestLongHeaders {
    @Test
    void testTooBigHeader() {
        WebServer ws = WebServer.builder()
                .routing(Routing.builder()
                                 .get("/", (req, res) -> res.send("Hi"))
                        .register("/static", StaticContentSupport.create("/static"))
                                 .build())
                .build()
                .start()
                .await(10, TimeUnit.SECONDS);

        WebClient client = WebClient.builder()
                .baseUri("http://localhost:" + ws.port())
                .build();

        String headerValue = header(8000);
        WebClientRequestBuilder builder = client.get();
        builder.headers().add("X_SHORT", headerValue);

        String result = builder.request(String.class).await(10, TimeUnit.SECONDS);

        assertThat(result, is("Hi"));

        headerValue = header(9000);
//        builder = client.get();
//        builder.headers().add("X_LONG", headerValue);
//
//        result = builder.request(String.class).await(10, TimeUnit.SECONDS);

//        assertThat(result, is("Hi"));

        builder = client.get();
        builder.headers().add("X_LONG", headerValue);

        result = builder.path("/static/static-content.txt").request(String.class).await(10, TimeUnit.SECONDS);

        assertThat(result, is("Hi"));

    }

    private String header(int size) {
        return "a".repeat(Math.max(0, size));
    }
}
