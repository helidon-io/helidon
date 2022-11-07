/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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
package io.helidon.integrations.openapi.ui.cdi;

import java.util.List;

import io.helidon.common.http.MediaType;
import io.helidon.microprofile.openapi.OpenApiCdiExtension;
import io.helidon.microprofile.tests.junit5.AddBean;
import io.helidon.microprofile.tests.junit5.AddExtension;
import io.helidon.microprofile.tests.junit5.HelidonTest;

import jakarta.inject.Inject;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.stringContainsInOrder;

@HelidonTest
@AddExtension(OpenApiCdiExtension.class)
@AddExtension(OpenApiUiCdiExtension.class)
@AddBean(GreetResource.class)
@AddBean(GreetingProvider.class)
class TestUI {

    @Inject
    private WebTarget webTarget;

    @Test
    void checkIntroScreen() {
        Response response = webTarget
                .path("/openapi")
                .request(MediaType.APPLICATION_OPENAPI_YAML.toString())
                .get();
        assertThat("OpenAPI document status", response.getStatus(), is(200));
        assertThat("OpenAPI document",
                   response.readEntity(String.class),
                   stringContainsInOrder(List.of("3.0.0", "/greet")));

        response = webTarget
                .path("/openapi-ui")
                .request(MediaType.TEXT_HTML.toString())
                .get();

        assertThat("Intro screen GET status", response.getStatus(), is(200));
        String body = response.readEntity(String.class);
        assertThat("Intro screen body",
                   body,
                   stringContainsInOrder(
                           List.of("<title>Helidon OpenAPI U/I</title>",
                                   "href='https://helidon.io'")));
    }
}
