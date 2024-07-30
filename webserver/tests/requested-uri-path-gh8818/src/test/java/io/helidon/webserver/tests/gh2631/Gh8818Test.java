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

package io.helidon.webserver.tests.gh2631;

import io.helidon.webclient.http1.Http1Client;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
class Gh8818Test {

    private Http1Client client;

    Gh8818Test(Http1Client client) {
        this.client = client;
    }

   @SetUpRoute
    static void setupRoute(HttpRouting.Builder routing) {
        Gh8818.routing(routing);
    }

    @Test
    void checkForFullPath() {
        String requestedPath = get(Gh8818.ENDPOINT_PATH);
        assertThat("Requested path", requestedPath, is(Gh8818.ENDPOINT_PATH));
    }

    private String get(String path) {
        return client.get()
                .path(path)
                .requestEntity(String.class);
    }
}