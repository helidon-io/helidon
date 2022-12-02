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
package io.helidon.integrations.openapi.ui;

import io.helidon.common.http.Http;

import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

class CdiTestsUtil {

    static void checkForPath(WebTarget webTarget, String path) {
        Response response = webTarget.path(path)
                .request(MediaType.TEXT_HTML)
                .get();
        assertThat("HTTP status accessing default UI endpoint",
                   response.getStatus(), is(Http.Status.OK_200.code()));
        assertThat("Content accessing default UI endpoint",
                   response.readEntity(String.class),
                   allOf(containsString("<html"),
                         containsString("swagger-ui")));
    }
}
