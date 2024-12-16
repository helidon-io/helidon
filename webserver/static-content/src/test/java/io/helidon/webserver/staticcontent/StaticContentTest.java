/*
 * Copyright (c) 2021, 2024 Oracle and/or its affiliates.
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

import java.nio.file.Files;
import java.nio.file.Path;

import io.helidon.common.testing.http.junit5.HttpHeaderMatcher;
import io.helidon.http.HeaderNames;
import io.helidon.http.Status;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.testing.junit5.DirectClient;
import io.helidon.webserver.testing.junit5.RoutingTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static io.helidon.webserver.staticcontent.StaticContentFeature.createService;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@RoutingTest
class StaticContentTest {
    @TempDir
    static Path tempDir;

    private final DirectClient testClient;

    StaticContentTest(DirectClient testClient) {
        this.testClient = testClient;
    }

    @SuppressWarnings("removal")
    @SetUpRoute
    static void setupRouting(HttpRouting.Builder builder) throws Exception {
        Path nested = tempDir.resolve("nested");
        Files.createDirectories(nested);

        Path resource = tempDir.resolve("resource.txt");
        Path favicon = tempDir.resolve("favicon.ico");

        Files.writeString(resource, "Content");
        Files.writeString(favicon, "Wrong icon text");
        Files.writeString(nested.resolve("resource.txt"), "Nested content");

        builder.register("/classpath", createService(ClasspathHandlerConfig.create("web")))
                .register("/singleclasspath", createService(ClasspathHandlerConfig.create("web/resource.txt")))
                .register("/path", createService(FileSystemHandlerConfig.create(tempDir)))
                .register("/singlepath", createService(FileSystemHandlerConfig.create(resource)));

        builder.register("/backward-comp/classpath", StaticContentService.builder("web"))
                .register("/backward-comp/singleclasspath", StaticContentService.builder("web/resource.txt"))
                .register("/backward-comp/path", StaticContentService.builder(tempDir))
                .register("/backward-comp/singlepath", StaticContentService.builder(resource));
    }

    @Test
    void testClasspathFavicon() {
        try (Http1ClientResponse response = testClient.get("/classpath/favicon.ico")
                .request()) {

            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.CONTENT_TYPE, "image/x-icon"));
        }
    }

    @Test
    void testClasspathFaviconBackwardComp() {
        try (Http1ClientResponse response = testClient.get("/backward-comp/classpath/favicon.ico")
                .request()) {

            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.CONTENT_TYPE, "image/x-icon"));
        }
    }

    @Test
    void testClasspathNested() {
        try (Http1ClientResponse response = testClient.get("/classpath/nested/resource.txt")
                .request()) {

            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.CONTENT_TYPE, "text/plain"));
            assertThat(response.as(String.class), is("Nested content"));
        }
    }

    @Test
    void testClasspathNestedBackwardComp() {
        try (Http1ClientResponse response = testClient.get("/backward-comp/classpath/nested/resource.txt")
                .request()) {

            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.CONTENT_TYPE, "text/plain"));
            assertThat(response.as(String.class), is("Nested content"));
        }
    }

    @Test
    void testClasspathSingleFile() {
        try (Http1ClientResponse response = testClient.get("/singleclasspath")
                .request()) {

            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.CONTENT_TYPE, "text/plain"));
            assertThat(response.as(String.class), is("Content"));
        }
    }

    @Test
    void testClasspathSingleFileBackwardComp() {
        try (Http1ClientResponse response = testClient.get("/backward-comp/singleclasspath")
                .request()) {

            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.CONTENT_TYPE, "text/plain"));
            assertThat(response.as(String.class), is("Content"));
        }
    }

    @Test
    void testFileSystemFavicon() {
        try (Http1ClientResponse response = testClient.get("/path/favicon.ico")
                .request()) {

            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.CONTENT_TYPE, "image/x-icon"));
        }
    }

    @Test
    void testFileSystemFaviconBackwardComp() {
        try (Http1ClientResponse response = testClient.get("/backward-comp/path/favicon.ico")
                .request()) {

            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.CONTENT_TYPE, "image/x-icon"));
        }
    }

    @Test
    void testFileSystemNested() {
        try (Http1ClientResponse response = testClient.get("/path/nested/resource.txt")
                .request()) {

            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.CONTENT_TYPE, "text/plain"));
            assertThat(response.as(String.class), is("Nested content"));
        }
    }


    @Test
    void testFileSystemNestedBackwardComp() {
        try (Http1ClientResponse response = testClient.get("/backward-comp/path/nested/resource.txt")
                .request()) {

            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.CONTENT_TYPE, "text/plain"));
            assertThat(response.as(String.class), is("Nested content"));
        }
    }

    @Test
    void testFileSystemSingleFile() {
        try (Http1ClientResponse response = testClient.get("/singlepath")
                .request()) {

            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.CONTENT_TYPE, "text/plain"));
            assertThat(response.as(String.class), is("Content"));
        }
    }

    @Test
    void testFileSystemSingleFileBackwardComp() {
        try (Http1ClientResponse response = testClient.get("/backward-comp/singlepath")
                .request()) {

            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.CONTENT_TYPE, "text/plain"));
            assertThat(response.as(String.class), is("Content"));
        }
    }
}
