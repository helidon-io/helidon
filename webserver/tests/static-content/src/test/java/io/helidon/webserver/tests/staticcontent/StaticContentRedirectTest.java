/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.webserver.tests.staticcontent;

import java.util.List;

import io.helidon.http.Status;
import io.helidon.webclient.api.ClientResponseTyped;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webserver.spi.ServerFeature;
import io.helidon.webserver.staticcontent.StaticContentFeature;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpFeatures;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
class StaticContentRedirectTest {
    private final Http1Client client;

    StaticContentRedirectTest(Http1Client client) {
        this.client = Http1Client.builder()
                .from(client.prototype())
                .followRedirects(false)
                .build();
    }

    @SetUpFeatures
    static List<ServerFeature> setUpFeatures() {
//        return List.of(StaticContentFeature.builder()
//                               .addPath(cp -> cp.context("/static/{+}")
//                                       .location(Paths.get("/home/tomas/Projects/oracle/helidon/helidon_4/webserver/tests/static-content/src/test/resources/static/welcome.txt")))
//                               .build());
        return List.of(StaticContentFeature.builder()
                               .addClasspath(cp -> cp.context("/static/{+}")
                                       .location("/static/welcome.txt"))
                               .build());
    }

    @Test
    void testContent() {
        ClientResponseTyped<String> response = client.get("/static/first")
                .request(String.class);

        assertThat(response.status(), is(Status.OK_200));
        assertThat(response.entity(), is("Welcome"));
    }

    @Test
    void testContentNested() {
        // as we use a single static content page, this should return the same page as the test above
        ClientResponseTyped<String> response = client.get("/static/nested")
                .request(String.class);

        assertThat(response.status(), is(Status.OK_200));
        assertThat(response.entity(), is("Welcome"));
    }
}
