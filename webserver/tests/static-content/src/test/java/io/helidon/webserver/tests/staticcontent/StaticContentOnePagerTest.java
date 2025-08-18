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
import java.util.stream.Stream;

import io.helidon.http.Status;
import io.helidon.webclient.api.ClientResponseTyped;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webserver.spi.ServerFeature;
import io.helidon.webserver.staticcontent.StaticContentFeature;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpFeatures;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
class StaticContentOnePagerTest {
    private final Http1Client client;

    StaticContentOnePagerTest(Http1Client client) {
        this.client = Http1Client.builder()
                .from(client.prototype())
                .followRedirects(false)
                .build();
    }

    @SetUpFeatures
    static List<ServerFeature> setUpFeatures() {
        return List.of(StaticContentFeature.builder()
                               .addClasspath(cp -> cp.context("/static")
                                       .location("/static/welcome.txt")
                                       .singleFile(true))
                               .build());
    }

    static Stream<TestData> testData() {
        return Stream.of(new TestData("/static"),
                         new TestData("/static/"),
                         new TestData("/static/nested"),
                         new TestData("/static/nested/"),
                         new TestData("/staticbad", Status.NOT_FOUND_404));
    }

    @ParameterizedTest
    @MethodSource("testData")
    void testBasePath(TestData testData) {
        ClientResponseTyped<String> response = client.get(testData.path())
                .request(String.class);

        assertThat(response.status(), is(testData.expectedStatus));

        if (testData.expectedStatus().equals(Status.OK_200)) {
            assertThat(response.entity(), is("Welcome"));
        }
    }

    private record TestData(String path, Status expectedStatus) {
        TestData(String path) {
            this(path, Status.OK_200);
        }
    }
}
