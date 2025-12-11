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
package io.helidon.tests.integration.openapiui.module;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.http.Status;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webserver.testing.junit5.ServerTest;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@ServerTest
class MainIntegrationTest extends AbstractMainTest {

    private final Http1Client client;

    MainIntegrationTest(Http1Client client) {
        super(client);
        this.client = client;
    }

    @Test
    void testOpenApiUi() {
        try (Http1ClientResponse response = client.get("/openapi")
                .followRedirects(true)
                .accept(MediaTypes.TEXT_HTML)
                .request()) {
            assertThat("OpenApiUi status", response.status(), is(Status.OK_200));
            String openApiDocument = response.entity().as(String.class);
            assertThat("OpenApiUi content", openApiDocument.contains("<div id=\"swagger-ui\"></div>"));
        }
    }
}