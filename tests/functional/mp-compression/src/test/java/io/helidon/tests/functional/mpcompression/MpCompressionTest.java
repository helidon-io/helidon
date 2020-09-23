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

package io.helidon.tests.functional.mpcompression;

import javax.inject.Inject;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;

import io.helidon.microprofile.tests.junit5.HelidonTest;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@HelidonTest
class MpCompressionTest {
    private final WebTarget target;

    @Inject
    MpCompressionTest(WebTarget baseTarget) {
        target = baseTarget.path("/compressed");
    }

    @Test
    void testGzip() {
        Response response = target.request().header("accept-encoding", "gzip").get();
        assertOk(response, "Hello World: gzip");
    }

    @Test
    void testDeflate() throws Exception {
        Response response = target.request().header("accept-encoding", "deflate").get();
        assertOk(response, "Hello World: deflate");
    }

    @Test
    void testDefault() {
        Response response = target.request().get();
        // client default is gzip
        assertOk(response, "Hello World: gzip");
    }

    private void assertOk(Response response, String expectedMessage) {
        assertThat(response.getStatus(), is(200));
        assertThat(response.readEntity(String.class), is(expectedMessage));
    }
}
