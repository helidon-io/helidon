/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

package io.helidon.webserver.staticcontent;

import java.util.concurrent.TimeoutException;

import io.helidon.common.http.Http;
import io.helidon.webserver.Routing;
import io.helidon.webserver.testsupport.TestClient;
import io.helidon.webserver.testsupport.TestResponse;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class StaticContentTest {
    private static TestClient testClient;

    @BeforeAll
    static void setupRouting() {
        Routing routing = Routing.builder()
                .register("/classpath", StaticContentSupport.builder("web"))
                .build();

        testClient = TestClient.create(routing);
    }

    @Test
    void testFavicon() throws TimeoutException, InterruptedException {
        TestResponse testResponse = testClient.path("/classpath/favicon.ico")
                .get();

        assertThat(testResponse.status(), is(Http.Status.OK_200));
    }
}
