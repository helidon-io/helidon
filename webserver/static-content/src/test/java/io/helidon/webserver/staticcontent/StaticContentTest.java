/*
 * Copyright (c) 2021, 2026 Oracle and/or its affiliates.
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

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
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@RoutingTest
class StaticContentTest {
    @TempDir
    static Path tempDir;
    private static Path staticRoot;
    private static Path externalDir;
    private static Path alternateRoot;
    private static Path rootLink;
    private static Path singleLink;
    private static Path singleParentLink;

    private final DirectClient testClient;

    StaticContentTest(DirectClient testClient) {
        this.testClient = testClient;
    }

    @SuppressWarnings("removal")
    @SetUpRoute
    static void setupRouting(HttpRouting.Builder builder) throws Exception {
        staticRoot = tempDir.resolve("static-root");
        externalDir = tempDir.resolve("outside-root");
        alternateRoot = tempDir.resolve("alternate-root");
        Path nested = staticRoot.resolve("nested");
        Path welcome = staticRoot.resolve("welcome");
        Files.createDirectories(nested);
        Files.createDirectories(welcome);
        Files.createDirectories(externalDir);
        Files.createDirectories(alternateRoot);

        Path resource = staticRoot.resolve("resource.txt");
        Path favicon = staticRoot.resolve("favicon.ico");

        Files.writeString(resource, "Content");
        Files.writeString(favicon, "Wrong icon text");
        Files.writeString(nested.resolve("resource.txt"), "Nested content");
        Files.writeString(staticRoot.resolve("alias-one.txt"), "Alias one");
        Files.writeString(staticRoot.resolve("alias-two.txt"), "Alias two");
        Files.writeString(externalDir.resolve("resource.txt"), "External content");
        Files.writeString(alternateRoot.resolve("resource.txt"), "Alternate content");

        builder.register("/classpath", createService(ClasspathHandlerConfig.create("web")))
                .register("/singleclasspath", createService(ClasspathHandlerConfig.create("web/resource.txt")))
                .register("/path", createService(FileSystemHandlerConfig.create(staticRoot)))
                .register("/singlepath", createService(FileSystemHandlerConfig.create(resource)));

        builder.register("/backward-comp/classpath", StaticContentService.builder("web"))
                .register("/backward-comp/singleclasspath", StaticContentService.builder("web/resource.txt"))
                .register("/backward-comp/path", StaticContentService.builder(staticRoot))
                .register("/backward-comp/singlepath", StaticContentService.builder(resource));
        builder.register("/welcome-path", StaticContentService.builder(staticRoot).welcomeFileName("index.html"));

        rootLink = tempDir.resolve("current-root");
        if (createSymbolicLink(rootLink, staticRoot)) {
            builder.register("/linkroot", createService(FileSystemHandlerConfig.create(rootLink)));
        } else {
            rootLink = null;
        }
        singleLink = tempDir.resolve("current-file");
        if (createSymbolicLink(singleLink, resource)) {
            builder.register("/singlelink", createService(FileSystemHandlerConfig.create(singleLink)));
        } else {
            singleLink = null;
        }
        singleParentLink = tempDir.resolve("current-parent");
        if (createSymbolicLink(singleParentLink, staticRoot)) {
            builder.register("/singleparentlink", createService(FileSystemHandlerConfig.builder()
                                                                      .location(singleParentLink.resolve("resource.txt"))
                                                                      .cachedFiles(Set.of("."))
                                                                      .build()));
        } else {
            singleParentLink = null;
        }
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
    @SuppressWarnings("removal")
    void testClasspathBuilderWithNullContextClassLoader() {
        ClassLoader original = Thread.currentThread().getContextClassLoader();

        try {
            Thread.currentThread().setContextClassLoader(null);

            StaticContentService service = StaticContentService.builder("web").build();

            assertThat(service, notNullValue());
        } finally {
            Thread.currentThread().setContextClassLoader(original);
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
    void testFileSystemNested() throws IOException {
        Status expectedStatus = StaticContentTestSupport.supportsSecureDirectoryStream(staticRoot)
                ? Status.OK_200
                : Status.FORBIDDEN_403;
        try (Http1ClientResponse response = testClient.get("/path/nested/resource.txt")
                .request()) {

            assertThat(response.status(), is(expectedStatus));
            if (Status.OK_200.equals(expectedStatus)) {
                assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.CONTENT_TYPE, "text/plain"));
                assertThat(response.as(String.class), is("Nested content"));
            }
        }
    }


    @Test
    void testFileSystemNestedBackwardComp() throws IOException {
        Status expectedStatus = StaticContentTestSupport.supportsSecureDirectoryStream(staticRoot)
                ? Status.OK_200
                : Status.FORBIDDEN_403;
        try (Http1ClientResponse response = testClient.get("/backward-comp/path/nested/resource.txt")
                .request()) {

            assertThat(response.status(), is(expectedStatus));
            if (Status.OK_200.equals(expectedStatus)) {
                assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.CONTENT_TYPE, "text/plain"));
                assertThat(response.as(String.class), is("Nested content"));
            }
        }
    }

    @Test
    void testFileSystemSymlinkOutsideRoot() throws Exception {
        Path link = staticRoot.resolve("external");
        assumeTrue(createSymbolicLink(link, externalDir), "Symbolic links cannot be created");

        try (Http1ClientResponse response = testClient.get("/path/external/resource.txt")
                .request()) {

            assertThat(response.status(), is(Status.NOT_FOUND_404));
        }
    }

    @Test
    void testFileSystemSymlinkRetargeting() throws Exception {
        Path link = staticRoot.resolve("alias.txt");
        assumeTrue(createSymbolicLink(link, staticRoot.resolve("alias-one.txt")), "Symbolic links cannot be created");

        try (Http1ClientResponse response = testClient.get("/path/alias.txt")
                .request()) {

            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.as(String.class), is("Alias one"));
        }

        assumeTrue(createSymbolicLink(link, staticRoot.resolve("alias-two.txt")), "Symbolic links cannot be retargeted");

        try (Http1ClientResponse response = testClient.get("/path/alias.txt")
                .request()) {

            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.as(String.class), is("Alias two"));
        }

        assumeTrue(createSymbolicLink(link, externalDir.resolve("resource.txt")), "Symbolic links cannot be retargeted");

        try (Http1ClientResponse response = testClient.get("/path/alias.txt")
                .request()) {

            assertThat(response.status(), is(Status.NOT_FOUND_404));
        }
    }

    @Test
    void testFileSystemSymlinkRangeRetargeting() throws Exception {
        Path link = staticRoot.resolve("range-alias.txt");
        assumeTrue(createSymbolicLink(link, staticRoot.resolve("alias-one.txt")), "Symbolic links cannot be created");

        try (Http1ClientResponse response = testClient.get("/path/range-alias.txt")
                .header(HeaderNames.RANGE, "bytes=0-4")
                .request()) {

            assertThat(response.status(), is(Status.PARTIAL_CONTENT_206));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.CONTENT_RANGE, "bytes 0-4/9"));
            assertThat(response.as(String.class), is("Alias"));
        }

        assumeTrue(createSymbolicLink(link, externalDir.resolve("resource.txt")), "Symbolic links cannot be retargeted");

        try (Http1ClientResponse response = testClient.get("/path/range-alias.txt")
                .header(HeaderNames.RANGE, "bytes=0-4")
                .request()) {

            assertThat(response.status(), is(Status.NOT_FOUND_404));
        }
    }

    @Test
    void testFileSystemWelcomeFileSymlinkOutsideRoot() throws Exception {
        Path link = staticRoot.resolve("welcome").resolve("index.html");
        assumeTrue(createSymbolicLink(link, externalDir.resolve("resource.txt")), "Symbolic links cannot be created");

        try (Http1ClientResponse response = testClient.get("/welcome-path/welcome/")
                .request()) {

            assertThat(response.status(), is(Status.NOT_FOUND_404));
        }
    }

    @Test
    void testFileSystemSymlinkRootRetargeting() throws Exception {
        assumeTrue(rootLink != null, "Symbolic links cannot be created");

        try (Http1ClientResponse response = testClient.get("/linkroot/resource.txt")
                .request()) {

            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.as(String.class), is("Content"));
        }

        assumeTrue(createSymbolicLink(rootLink, alternateRoot), "Symbolic links cannot be retargeted");

        try (Http1ClientResponse response = testClient.get("/linkroot/resource.txt")
                .request()) {

            assertThat(response.status(), is(Status.NOT_FOUND_404));
        }
    }

    @Test
    void testFileSystemSingleFileSymlinkRetargeting() throws Exception {
        assumeTrue(singleLink != null, "Symbolic links cannot be created");

        try (Http1ClientResponse response = testClient.get("/singlelink")
                .request()) {

            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.as(String.class), is("Content"));
        }

        assumeTrue(createSymbolicLink(singleLink, externalDir.resolve("resource.txt")), "Symbolic links cannot be retargeted");

        try (Http1ClientResponse response = testClient.get("/singlelink")
                .request()) {

            assertThat(response.status(), is(Status.NOT_FOUND_404));
        }
    }

    @Test
    void testFileSystemSingleFileCachedParentSymlinkRetargeting() throws Exception {
        assumeTrue(singleParentLink != null, "Symbolic links cannot be created");

        try (Http1ClientResponse response = testClient.get("/singleparentlink")
                .request()) {

            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.as(String.class), is("Content"));
        }

        assumeTrue(createSymbolicLink(singleParentLink, alternateRoot), "Symbolic links cannot be retargeted");

        try (Http1ClientResponse response = testClient.get("/singleparentlink")
                .request()) {

            assertThat(response.status(), is(Status.NOT_FOUND_404));
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

    private static boolean createSymbolicLink(Path link, Path target) throws IOException {
        Files.deleteIfExists(link);
        try {
            Files.createSymbolicLink(link, target);
            return true;
        } catch (UnsupportedOperationException | IOException | SecurityException e) {
            return false;
        }
    }
}
