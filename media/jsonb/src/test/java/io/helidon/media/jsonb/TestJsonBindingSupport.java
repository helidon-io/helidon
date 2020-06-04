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
package io.helidon.media.jsonb;

import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;

import io.helidon.common.GenericType;
import io.helidon.common.http.Http;
import io.helidon.common.http.MediaType;
import io.helidon.webserver.Handler;
import io.helidon.webserver.Routing;
import io.helidon.webserver.testsupport.MediaPublisher;
import io.helidon.webserver.testsupport.TestClient;
import io.helidon.webserver.testsupport.TestResponse;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Tests {@link JsonbSupport}.
 */
public class TestJsonBindingSupport {

    @Test
    public void pingPong() throws Exception {
        final Routing routing = Routing.builder()
            .post("/foo", Handler.create(Person.class, (req, res, person) -> res.send(person)))
            .build();
        final String personJson = "{\"name\":\"Frank\"}";
        final TestResponse response = TestClient.create(routing, JsonbSupport.create())
            .path("/foo")
            .post(MediaPublisher.create(MediaType.APPLICATION_JSON.withCharset("UTF-8"), personJson));

        assertThat(response.headers().first(Http.Header.CONTENT_TYPE).orElse(null),
                is(MediaType.APPLICATION_JSON.toString()));
        final String json = response.asString().get(10, TimeUnit.SECONDS);
        assertThat(json, is(personJson));
    }

    @Test
    public void genericType() throws Exception {
        GenericType<List<Person>> personsType = new GenericType<>() { };
        final Routing routing = Routing.builder()
                .post("/foo", (req, res) -> {
                    req.content().as(personsType)
                            .thenAccept(res::send);
                })
                .build();

        final String personsJson = "[{\"name\":\"Frank\"},{\"name\":\"John\"}]";
        final TestResponse response = TestClient.create(routing, JsonbSupport.create())
            .path("/foo")
            .post(MediaPublisher.create(MediaType.APPLICATION_JSON.withCharset("UTF-8"),
                    personsJson));
        assertThat(response.headers().first(Http.Header.CONTENT_TYPE).orElse(null),
                is(MediaType.APPLICATION_JSON.toString()));
        final String json = response.asString().get(10, TimeUnit.SECONDS);
        assertThat(json, is(personsJson));
    }

    public static final class Person {

        private String name;

        public Person() {
            super();
        }

        public String getName() {
            return this.name;
        }

        public void setName(final String name) {
            this.name = name;
        }

    }
}
