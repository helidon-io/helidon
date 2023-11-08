/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.http.tests.integration.encoding.gzip;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;

import io.helidon.http.HeaderValues;
import io.helidon.webclient.api.WebClient;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.staticcontent.StaticContentService;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.webserver.testing.junit5.SetUpServer;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@ServerTest
class GzipEncodingStaticContentTest {

    @SetUpRoute
    static void routing(HttpRouting.Builder builder) {
        builder.register("/", StaticContentService.builder("/WEB")
                .welcomeFileName("index.html")
                .build());
    }

    @Test
    void compressedStaticContent(WebClient client) throws IOException, URISyntaxException {
        try (var res = client.get("/")
                .header(HeaderValues.createCached("Accept-Encoding", "deflate, gzip"))
                .request()) {
            assertThat(res.as(String.class),
                       is(Files.readString(Path.of(this.getClass().getResource("/WEB/index.html").toURI()))));
        }
    }
}
