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
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Set;

import io.helidon.common.testing.http.junit5.HttpHeaderMatcher;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;
import io.helidon.http.encoding.ContentDecoder;
import io.helidon.http.encoding.ContentEncoder;
import io.helidon.http.encoding.ContentEncoding;
import io.helidon.http.encoding.ContentEncodingContext;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.testing.junit5.DirectClient;
import io.helidon.webserver.testing.junit5.RoutingTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.webserver.testing.junit5.SetUpServer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static io.helidon.webserver.staticcontent.StaticContentFeature.createService;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@RoutingTest
class StaticContentTest {
    @TempDir
    static Path tempDir;
    private static Path staticRoot;
    private static Path externalDir;
    private static Path rootLink;
    private static Path singleLink;
    private static Path singleSidecarLink;
    private static Path singleParentLink;

    private final DirectClient testClient;

    StaticContentTest(DirectClient testClient) {
        this.testClient = testClient;
    }

    @SetUpServer
    static void setupServer(WebServerConfig.Builder builder) {
        builder.contentEncoding(ContentEncodingContext.builder()
                .addContentEncoding(new TestEncoding())
                .build());
    }

    @SetUpRoute
    static void setupRouting(HttpRouting.Builder builder) throws Exception {
        staticRoot = tempDir.resolve("static-root");
        externalDir = tempDir.resolve("outside-root");
        Path nested = staticRoot.resolve("nested");
        Path welcome = staticRoot.resolve("welcome");
        Files.createDirectories(nested);
        Files.createDirectories(welcome);
        Files.createDirectories(externalDir);

        Path resource = staticRoot.resolve("resource.txt");
        Path empty = staticRoot.resolve("empty.txt");
        Path favicon = staticRoot.resolve("favicon.ico");

        Files.writeString(resource, "Content");
        Files.writeString(empty, "");
        Files.writeString(staticRoot.resolve("resource.txt.br"), "Brotli content");
        Files.writeString(staticRoot.resolve("resource.txt.gz"), "Gzip content");
        Files.writeString(favicon, "Wrong icon text");
        Files.writeString(nested.resolve("resource.txt"), "Nested content");
        Files.writeString(staticRoot.resolve("alias-one.txt"), "Alias one");
        Files.writeString(staticRoot.resolve("alias-two.txt"), "Alias two");
        Path shortEtag = staticRoot.resolve("etag-short.txt");
        Path longEtag = staticRoot.resolve("etag-long.txt");
        Files.writeString(shortEtag, "short");
        Files.writeString(longEtag, "longer content");
        FileTime commonLastModified = FileTime.fromMillis(System.currentTimeMillis() - 10_000);
        Files.setLastModifiedTime(shortEtag, commonLastModified);
        Files.setLastModifiedTime(longEtag, commonLastModified);
        Files.writeString(externalDir.resolve("resource.txt"), "External content");

        builder.any("/vary-path/*",
                    (request, response) -> response.header(HeaderNames.VARY, HeaderNames.ORIGIN_NAME).next())
                .register("/vary-path", createService(FileSystemHandlerConfig.create(staticRoot)))
                .register("/classpath", createService(ClasspathHandlerConfig.create("web")))
                .register("/classpath-memory", createService(ClasspathHandlerConfig.builder()
                                                                      .location("web")
                                                                      .cachedFiles(Set.of("resource.txt"))
                                                                      .build()))
                .register("/singleclasspath", createService(ClasspathHandlerConfig.create("web/resource.txt")))
                .register("/path", createService(FileSystemHandlerConfig.create(staticRoot)))
                .register("/path-memory", createService(FileSystemHandlerConfig.builder()
                                                                .location(staticRoot)
                                                                .cachedFiles(Set.of("empty.txt"))
                                                                .build()))
                .register("/path-disabled", createService(FileSystemHandlerConfig.builder()
                        .location(staticRoot)
                        .preCompressedEnabled(false)
                        .build()))
                .register("/singlepath", createService(FileSystemHandlerConfig.create(resource)));

        builder.register("/welcome-path", createService(FileSystemHandlerConfig.builder()
                                                   .location(staticRoot)
                                                   .welcome("index.html")
                                                   .build()));

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
        singleSidecarLink = tempDir.resolve("current-sidecar-file");
        if (createSymbolicLink(singleSidecarLink, resource)) {
            builder.register("/single-sidecar-link", createService(FileSystemHandlerConfig.create(singleSidecarLink)));
        } else {
            singleSidecarLink = null;
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
    void testClasspathNested() {
        try (Http1ClientResponse response = testClient.get("/classpath/nested/resource.txt")
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
    void testClasspathSingleFilePreCompressed() {
        try (Http1ClientResponse response = testClient.get("/singleclasspath")
                .header(HeaderNames.ACCEPT_ENCODING, "br")
                .request()) {

            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.CONTENT_TYPE, "text/plain"));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.CONTENT_ENCODING, "br"));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.VARY, HeaderNames.ACCEPT_ENCODING_NAME));
            assertThat(response.as(String.class), is("Brotli content\n"));
        }
    }

    @Test
    void testClasspathPreCompressed() {
        try (Http1ClientResponse response = testClient.get("/classpath/resource.txt")
                .header(HeaderNames.ACCEPT_ENCODING, "br")
                .request()) {

            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.CONTENT_TYPE, "text/plain"));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.CONTENT_ENCODING, "br"));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.VARY, HeaderNames.ACCEPT_ENCODING_NAME));
            assertThat(response.as(String.class), is("Brotli content\n"));
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
    void testFileSystemPreCompressedPrefersBrotli() {
        try (Http1ClientResponse response = testClient.get("/path/resource.txt")
                .header(HeaderNames.ACCEPT_ENCODING, "br;q=1, gzip;q=0.8")
                .request()) {

            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.CONTENT_TYPE, "text/plain"));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.CONTENT_ENCODING, "br"));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.VARY, HeaderNames.ACCEPT_ENCODING_NAME));
            assertThat(response.as(String.class), is("Brotli content"));
        }
    }

    @Test
    void testFileSystemPreCompressedUsesGzipSidecar() {
        try (Http1ClientResponse response = testClient.get("/path/resource.txt")
                .header(HeaderNames.ACCEPT_ENCODING, "gzip")
                .request()) {

            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.CONTENT_TYPE, "text/plain"));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.CONTENT_ENCODING, "gzip"));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.VARY, HeaderNames.ACCEPT_ENCODING_NAME));
            assertThat(response.as(String.class), is("Gzip content"));
        }
    }

    @Test
    void testFileSystemPreCompressedUsesIdentityWhenSidecarMissing() {
        try (Http1ClientResponse response = testClient.get("/path/nested/resource.txt")
                .header(HeaderNames.ACCEPT_ENCODING, "gzip")
                .request()) {

            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.headers(), HttpHeaderMatcher.noHeader(HeaderNames.CONTENT_ENCODING));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.VARY, HeaderNames.ACCEPT_ENCODING_NAME));
            assertThat(response.as(String.class), is("Nested content"));
        }
    }

    @Test
    void testFileSystemPreCompressedAdditionAfterMiss() throws IOException {
        Path identity = staticRoot.resolve("sidecar-added-after-miss.txt");
        Path sidecar = staticRoot.resolve("sidecar-added-after-miss.txt.br");
        Files.writeString(identity, "Identity content");
        Files.deleteIfExists(sidecar);

        try {
            try (Http1ClientResponse response = testClient.get("/path/sidecar-added-after-miss.txt")
                    .header(HeaderNames.ACCEPT_ENCODING, "br")
                    .request()) {

                assertThat(response.status(), is(Status.OK_200));
                assertThat(response.headers(), HttpHeaderMatcher.noHeader(HeaderNames.CONTENT_ENCODING));
                assertThat(response.as(String.class), is("Identity content"));
            }

            Files.writeString(sidecar, "Brotli content");

            try (Http1ClientResponse response = testClient.get("/path/sidecar-added-after-miss.txt")
                    .header(HeaderNames.ACCEPT_ENCODING, "br")
                    .request()) {

                assertThat(response.status(), is(Status.OK_200));
                assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.CONTENT_ENCODING, "br"));
                assertThat(response.as(String.class), is("Brotli content"));
            }
        } finally {
            Files.deleteIfExists(sidecar);
            Files.deleteIfExists(identity);
        }
    }

    @Test
    void testFileSystemPreCompressedRejectsWhenIdentityRejectedWithoutListenerContext() {
        try (Http1ClientResponse response = testClient.get("/path/nested/resource.txt")
                .header(HeaderNames.ACCEPT_ENCODING, "gzip, identity;q=0")
                .request()) {

            assertThat(response.status(), is(Status.NOT_ACCEPTABLE_406));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.VARY, HeaderNames.ACCEPT_ENCODING_NAME));
        }
    }

    @Test
    void testFileSystemPreCompressedUsesIdentityRangeWhenSidecarMissing() {
        try (Http1ClientResponse response = testClient.get("/path/nested/resource.txt")
                .header(HeaderNames.ACCEPT_ENCODING, "gzip")
                .header(HeaderNames.RANGE, "bytes=0-3")
                .request()) {

            assertThat(response.status(), is(Status.PARTIAL_CONTENT_206));
            assertThat(response.headers(), HttpHeaderMatcher.noHeader(HeaderNames.CONTENT_ENCODING));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.CONTENT_RANGE));
            assertThat(response.as(String.class), is("Nest"));
        }
    }

    @Test
    void testFileSystemPreCompressedUsesSidecarRangeWhenSidecarExists() {
        try (Http1ClientResponse response = testClient.get("/path/resource.txt")
                .header(HeaderNames.ACCEPT_ENCODING, "br")
                .header(HeaderNames.RANGE, "bytes=2-5")
                .request()) {

            assertThat(response.status(), is(Status.PARTIAL_CONTENT_206));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.CONTENT_ENCODING, "br"));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.CONTENT_RANGE, "bytes 2-5/14"));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.VARY, HeaderNames.ACCEPT_ENCODING_NAME));
            assertThat(response.as(String.class), is("otli"));
        }
    }

    @Test
    void testFileSystemPreCompressedUsesOversizedSuffixRange() {
        try (Http1ClientResponse response = testClient.get("/path/resource.txt")
                .header(HeaderNames.ACCEPT_ENCODING, "br")
                .header(HeaderNames.RANGE, "bytes=-500")
                .request()) {

            assertThat(response.status(), is(Status.PARTIAL_CONTENT_206));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.CONTENT_ENCODING, "br"));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.CONTENT_RANGE, "bytes 0-13/14"));
            assertThat(response.as(String.class), is("Brotli content"));
        }
    }

    @Test
    void testConditionalSidecarMergesVaryFromException() {
        String etag;
        try (Http1ClientResponse response = testClient.get("/vary-path/resource.txt")
                .header(HeaderNames.ACCEPT_ENCODING, "br")
                .request()) {
            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.headers(),
                       HttpHeaderMatcher.hasHeader(HeaderNames.VARY,
                                                   HeaderNames.ORIGIN_NAME,
                                                   HeaderNames.ACCEPT_ENCODING_NAME));
            etag = response.headers().get(HeaderNames.ETAG).get();
        }

        try (Http1ClientResponse response = testClient.get("/vary-path/resource.txt")
                .header(HeaderNames.ACCEPT_ENCODING, "br")
                .header(HeaderNames.IF_NONE_MATCH, etag)
                .request()) {

            assertThat(response.status(), is(Status.NOT_MODIFIED_304));
            assertThat(response.headers(),
                       HttpHeaderMatcher.hasHeader(HeaderNames.VARY,
                                                   HeaderNames.ORIGIN_NAME,
                                                   HeaderNames.ACCEPT_ENCODING_NAME));
        }
    }

    @Test
    void testFileSystemPreCompressedIfRangeUsesSelectedRepresentationEtag() {
        String identityEtag;
        try (Http1ClientResponse response = testClient.get("/path/resource.txt")
                .request()) {
            assertThat(response.status(), is(Status.OK_200));
            identityEtag = response.headers().get(HeaderNames.ETAG).get();
        }

        try (Http1ClientResponse response = testClient.get("/path/resource.txt")
                .header(HeaderNames.ACCEPT_ENCODING, "br")
                .header(HeaderNames.RANGE, "bytes=2-5")
                .header(HeaderNames.IF_RANGE, identityEtag)
                .request()) {

            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.CONTENT_ENCODING, "br"));
            assertThat(response.headers(), HttpHeaderMatcher.noHeader(HeaderNames.CONTENT_RANGE));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.VARY, HeaderNames.ACCEPT_ENCODING_NAME));
            assertThat(response.as(String.class), is("Brotli content"));
        }
    }

    @Test
    void testFileSystemPreCompressedIfRangeAllowsMatchingSelectedRepresentationEtag() {
        String brotliEtag;
        try (Http1ClientResponse response = testClient.get("/path/resource.txt")
                .header(HeaderNames.ACCEPT_ENCODING, "br")
                .request()) {
            assertThat(response.status(), is(Status.OK_200));
            brotliEtag = response.headers().get(HeaderNames.ETAG).get();
        }

        try (Http1ClientResponse response = testClient.get("/path/resource.txt")
                .header(HeaderNames.ACCEPT_ENCODING, "br")
                .header(HeaderNames.RANGE, "bytes=2-5")
                .header(HeaderNames.IF_RANGE, brotliEtag)
                .request()) {

            assertThat(response.status(), is(Status.PARTIAL_CONTENT_206));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.CONTENT_ENCODING, "br"));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.CONTENT_RANGE, "bytes 2-5/14"));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.VARY, HeaderNames.ACCEPT_ENCODING_NAME));
            assertThat(response.as(String.class), is("otli"));
        }
    }

    @Test
    void testFileSystemPreCompressedDisabled() {
        try (Http1ClientResponse response = testClient.get("/path-disabled/resource.txt")
                .header(HeaderNames.ACCEPT_ENCODING, "br")
                .request()) {

            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.headers(), HttpHeaderMatcher.noHeader(HeaderNames.CONTENT_ENCODING));
            assertThat(response.as(String.class), is("Content"));
        }
    }

    @Test
    void testFileSystemPreCompressedDisabledRangeDoesNotRuntimeEncode() {
        try (Http1ClientResponse response = testClient.get("/path-disabled/resource.txt")
                .header(HeaderNames.ACCEPT_ENCODING, "gzip")
                .header(HeaderNames.RANGE, "bytes=0-3")
                .request()) {

            assertThat(response.status(), is(Status.PARTIAL_CONTENT_206));
            assertThat(response.headers(), HttpHeaderMatcher.noHeader(HeaderNames.CONTENT_ENCODING));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.CONTENT_RANGE, "bytes 0-3/7"));
            assertThat(response.as(String.class), is("Cont"));
        }
    }

    @Test
    void testFileSystemPreCompressedDisabledRangeRejectsUnavailableIdentity() {
        try (Http1ClientResponse response = testClient.get("/path-disabled/resource.txt")
                .header(HeaderNames.ACCEPT_ENCODING, "gzip, identity;q=0")
                .header(HeaderNames.RANGE, "bytes=0-3")
                .request()) {

            assertThat(response.status(), is(Status.NOT_ACCEPTABLE_406));
            assertThat(response.headers(), HttpHeaderMatcher.noHeader(HeaderNames.CONTENT_ENCODING));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.VARY, HeaderNames.ACCEPT_ENCODING_NAME));
        }
    }

    @Test
    void testFileSystemPreCompressedRejectsQZero() {
        try (Http1ClientResponse response = testClient.get("/path/resource.txt")
                .header(HeaderNames.ACCEPT_ENCODING, "br;q=0")
                .request()) {

            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.headers(), HttpHeaderMatcher.noHeader(HeaderNames.CONTENT_ENCODING));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.VARY, HeaderNames.ACCEPT_ENCODING_NAME));
            assertThat(response.as(String.class), is("Content"));
        }
    }

    @Test
    void testFileSystemPreCompressedWildcardSelectsSidecarWhenIdentityRejected() {
        try (Http1ClientResponse response = testClient.get("/path/resource.txt")
                .header(HeaderNames.ACCEPT_ENCODING, "*, identity;q=0")
                .request()) {

            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.CONTENT_ENCODING, "br"));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.VARY, HeaderNames.ACCEPT_ENCODING_NAME));
            assertThat(response.as(String.class), is("Brotli content"));
        }
    }

    @Test
    void testFileSystemPreCompressedNoAcceptableRepresentation() {
        try (Http1ClientResponse response = testClient.get("/path/resource.txt")
                .header(HeaderNames.ACCEPT_ENCODING, "identity;q=0")
                .request()) {

            assertThat(response.status(), is(Status.NOT_ACCEPTABLE_406));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.VARY, HeaderNames.ACCEPT_ENCODING_NAME));
        }
    }

    @Test
    void testFileSystemPreCompressedUnknownCodingIsUnavailable() {
        try (Http1ClientResponse response = testClient.get("/path/resource.txt")
                .header(HeaderNames.ACCEPT_ENCODING, "zstd, identity;q=0")
                .request()) {

            assertThat(response.status(), is(Status.NOT_ACCEPTABLE_406));
            assertThat(response.headers(), HttpHeaderMatcher.noHeader(HeaderNames.CONTENT_ENCODING));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.VARY, HeaderNames.ACCEPT_ENCODING_NAME));
        }
    }

    @Test
    void testFileSystemPreCompressedRejectsInvalidAcceptEncoding() {
        try (Http1ClientResponse response = testClient.get("/path/resource.txt")
                .header(HeaderNames.ACCEPT_ENCODING, "g zip, identity;q=0")
                .request()) {

            assertThat(response.status(), is(Status.BAD_REQUEST_400));
            assertThat(response.headers(), HttpHeaderMatcher.noHeader(HeaderNames.CONTENT_ENCODING));
        }
    }

    @Test
    void testFileSystemPreCompressedDisabledHeadRejectsInvalidAcceptEncoding() throws IOException {
        try (Http1ClientResponse response = testClient.head("/path-disabled/resource.txt")
                .header(HeaderNames.ACCEPT_ENCODING, "g zip, identity;q=0")
                .request()) {

            assertThat(response.status(), is(Status.BAD_REQUEST_400));
            assertThat(response.headers(), HttpHeaderMatcher.noHeader(HeaderNames.CONTENT_ENCODING));
            assertThat(response.entity().inputStream().read(), is(-1));
        }
    }

    @Test
    void testFileSystemPreCompressedRejectsInvalidAcceptEncodingParameter() {
        try (Http1ClientResponse response = testClient.get("/path/resource.txt")
                .header(HeaderNames.ACCEPT_ENCODING, "gzip;level=1")
                .request()) {

            assertThat(response.status(), is(Status.BAD_REQUEST_400));
            assertThat(response.headers(), HttpHeaderMatcher.noHeader(HeaderNames.CONTENT_ENCODING));
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
    void testFileSystemAdditionAfterMiss() throws IOException {
        Path added = staticRoot.resolve("added-after-miss.txt");
        Files.deleteIfExists(added);

        try (Http1ClientResponse response = testClient.get("/path/added-after-miss.txt").request()) {
            assertThat(response.status(), is(Status.NOT_FOUND_404));
        }

        Files.writeString(added, "Added content");

        try (Http1ClientResponse response = testClient.get("/path/added-after-miss.txt").request()) {
            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.CONTENT_LENGTH, "13"));
            assertThat(response.headers().contains(HeaderNames.ETAG), is(true));
            assertThat(response.headers().contains(HeaderNames.LAST_MODIFIED), is(true));
            assertThat(response.as(String.class), is("Added content"));
        }
    }

    @Test
    void testFileSystemRemovalAndReaddition() throws IOException {
        Path removable = staticRoot.resolve("removed-and-readded.txt");
        Files.writeString(removable, "Initial content");

        try (Http1ClientResponse response = testClient.get("/path/removed-and-readded.txt").request()) {
            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.as(String.class), is("Initial content"));
        }

        Files.delete(removable);

        try (Http1ClientResponse response = testClient.get("/path/removed-and-readded.txt").request()) {
            assertThat(response.status(), is(Status.NOT_FOUND_404));
        }

        Files.writeString(removable, "Re-added content");

        try (Http1ClientResponse response = testClient.get("/path/removed-and-readded.txt").request()) {
            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.CONTENT_LENGTH, "16"));
            assertThat(response.as(String.class), is("Re-added content"));
        }
    }

    @Test
    void testEtagIncludesContentLength() {
        String shortEtag;
        try (Http1ClientResponse response = testClient.get("/path/etag-short.txt").request()) {
            shortEtag = response.headers().get(HeaderNames.ETAG).get();
        }

        String longEtag;
        try (Http1ClientResponse response = testClient.get("/path/etag-long.txt").request()) {
            longEtag = response.headers().get(HeaderNames.ETAG).get();
        }

        assertThat(longEtag, not(is(shortEtag)));
    }

    @Test
    void testIfNoneMatchTakesPrecedenceOverIfModifiedSince() {
        try (Http1ClientResponse response = testClient.get("/path/resource.txt")
                .header(HeaderNames.IF_NONE_MATCH, "\"different\"")
                .header(HeaderNames.IF_MODIFIED_SINCE, "Wed, 21 Oct 2099 07:28:00 GMT")
                .request()) {

            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.as(String.class), is("Content"));
        }
    }

    @Test
    void testMalformedIfNoneMatchIsIgnored() {
        for (String value : List.of("\"", "W/\"")) {
            try (Http1ClientResponse response = testClient.get("/path/resource.txt")
                    .header(HeaderNames.IF_NONE_MATCH, value)
                    .request()) {

                assertThat("If-None-Match: " + value, response.status(), is(Status.OK_200));
                assertThat("If-None-Match body for: " + value, response.as(String.class), is("Content"));
            }
        }
    }

    @Test
    void testMalformedIfMatchFailsPrecondition() {
        for (String value : List.of("\"", "W/\"")) {
            try (Http1ClientResponse response = testClient.get("/path/resource.txt")
                    .header(HeaderNames.IF_MATCH, value)
                    .request()) {

                assertThat("If-Match: " + value, response.status(), is(Status.PRECONDITION_FAILED_412));
            }
        }
    }

    @Test
    void testWildcardMustBeSoleConditionalEntityTagValue() {
        String etag;
        try (Http1ClientResponse response = testClient.get("/path/resource.txt")
                .request()) {
            etag = response.headers().get(HeaderNames.ETAG).get();
        }

        for (String value : List.of(etag + ", *", "*, " + etag)) {
            try (Http1ClientResponse response = testClient.get("/path/resource.txt")
                    .header(HeaderNames.IF_NONE_MATCH, value)
                    .request()) {

                assertThat("If-None-Match: " + value, response.status(), is(Status.OK_200));
                assertThat("If-None-Match body for: " + value, response.as(String.class), is("Content"));
            }

            try (Http1ClientResponse response = testClient.get("/path/resource.txt")
                    .header(HeaderNames.IF_MATCH, value)
                    .request()) {

                assertThat("If-Match: " + value, response.status(), is(Status.PRECONDITION_FAILED_412));
            }
        }
    }

    @Test
    void testInvalidIfModifiedSinceIsIgnored() {
        try (Http1ClientResponse response = testClient.get("/path/resource.txt")
                .header(HeaderNames.IF_MODIFIED_SINCE, "nope")
                .request()) {

            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.as(String.class), is("Content"));
        }
    }

    @Test
    void testMultipleIfModifiedSinceValuesAreIgnored() {
        try (Http1ClientResponse response = testClient.get("/path/resource.txt")
                .header(HeaderNames.IF_MODIFIED_SINCE,
                        "Wed, 21 Oct 2099 07:28:00 GMT",
                        "Wed, 21 Oct 2015 07:28:00 GMT")
                .request()) {

            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.as(String.class), is("Content"));
        }
    }

    @Test
    void testInvalidIfUnmodifiedSinceIsIgnored() {
        try (Http1ClientResponse response = testClient.get("/path/resource.txt")
                .header(HeaderNames.IF_UNMODIFIED_SINCE, "nope")
                .request()) {

            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.as(String.class), is("Content"));
        }
    }

    @Test
    void testMultipleIfUnmodifiedSinceValuesAreIgnored() {
        try (Http1ClientResponse response = testClient.get("/path/resource.txt")
                .header(HeaderNames.IF_UNMODIFIED_SINCE,
                        "Wed, 21 Oct 2015 07:28:00 GMT",
                        "Wed, 21 Oct 2099 07:28:00 GMT")
                .request()) {

            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.as(String.class), is("Content"));
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
    void testFileSystemDirectorySymlinkOutsideRootDoesNotRedirect() throws Exception {
        Path link = staticRoot.resolve("external-directory");
        assumeTrue(createSymbolicLink(link, externalDir), "Symbolic links cannot be created");

        try (Http1ClientResponse response = testClient.get("/welcome-path/external-directory")
                .followRedirects(false)
                .request()) {

            assertThat(response.status(), is(Status.NOT_FOUND_404));
        }
    }

    @Test
    void testFileSystemSymlinkInsideRoot() throws Exception {
        Path link = staticRoot.resolve("alias.txt");
        assumeTrue(createSymbolicLink(link, staticRoot.resolve("alias-one.txt")), "Symbolic links cannot be created");

        try (Http1ClientResponse response = testClient.get("/path/alias.txt")
                .request()) {

            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.as(String.class), is("Alias one"));
        }

    }

    @Test
    void testFileSystemSymlinkRange() throws Exception {
        Path link = staticRoot.resolve("range-alias.txt");
        assumeTrue(createSymbolicLink(link, staticRoot.resolve("alias-one.txt")), "Symbolic links cannot be created");

        try (Http1ClientResponse response = testClient.get("/path/range-alias.txt")
                .header(HeaderNames.RANGE, "bytes=0-4")
                .request()) {

            assertThat(response.status(), is(Status.PARTIAL_CONTENT_206));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.CONTENT_RANGE, "bytes 0-4/9"));
            assertThat(response.as(String.class), is("Alias"));
        }

    }

    @Test
    void testClasspathRangeWithNonZeroOffset() {
        try (Http1ClientResponse response = testClient.get("/classpath-memory/resource.txt")
                .header(HeaderNames.RANGE, "bytes=2-4")
                .request()) {

            assertThat(response.status(), is(Status.PARTIAL_CONTENT_206));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.CONTENT_RANGE, "bytes 2-4/7"));
            assertThat(response.as(String.class), is("nte"));
        }
    }

    @Test
    void testFileSystemRangeEndBeyondContent() {
        try (Http1ClientResponse response = testClient.get("/path/resource.txt")
                .header(HeaderNames.RANGE, "bytes=2-999")
                .request()) {

            assertThat(response.status(), is(Status.PARTIAL_CONTENT_206));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.CONTENT_RANGE, "bytes 2-6/7"));
            assertThat(response.as(String.class), is("ntent"));
        }
    }

    @Test
    void testFileSystemSuffixRangeBeyondContent() {
        try (Http1ClientResponse response = testClient.get("/path/resource.txt")
                .header(HeaderNames.RANGE, "bytes=-999")
                .request()) {

            assertThat(response.status(), is(Status.PARTIAL_CONTENT_206));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.CONTENT_RANGE, "bytes 0-6/7"));
            assertThat(response.as(String.class), is("Content"));
        }
    }

    @Test
    void testFileSystemEmptyRange() {
        assertEmptyRanges("/path/empty.txt");
    }

    @Test
    void testCachedEmptyRange() {
        assertEmptyRanges("/path-memory/empty.txt");
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
    void testFileSystemSymlinkRoot() {
        assumeTrue(rootLink != null, "Symbolic links cannot be created");

        try (Http1ClientResponse response = testClient.get("/linkroot/resource.txt")
                .request()) {

            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.as(String.class), is("Content"));
        }

    }

    @Test
    void testFileSystemSingleFileSymlink() {
        assumeTrue(singleLink != null, "Symbolic links cannot be created");

        try (Http1ClientResponse response = testClient.get("/singlelink")
                .request()) {

            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.as(String.class), is("Content"));
        }

    }

    @Test
    void testFileSystemSingleFileSidecarUsesPinnedTarget() throws Exception {
        assumeTrue(singleSidecarLink != null, "Symbolic links cannot be created");
        Files.writeString(singleSidecarLink.resolveSibling(singleSidecarLink.getFileName() + ".gz"),
                          "Untrusted gzip content");

        try (Http1ClientResponse response = testClient.get("/single-sidecar-link")
                .header(HeaderNames.ACCEPT_ENCODING, "gzip, identity;q=0")
                .request()) {

            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.CONTENT_ENCODING, "gzip"));
            assertThat(response.as(String.class), is("Gzip content"));
        }
    }

    @Test
    void testFileSystemSingleFileCachedParentSymlink() {
        assumeTrue(singleParentLink != null, "Symbolic links cannot be created");

        try (Http1ClientResponse response = testClient.get("/singleparentlink")
                .request()) {

            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.as(String.class), is("Content"));
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

    private void assertEmptyRanges(String path) {
        assertEmptyRangeIgnored(path, "bytes=-1");
        assertEmptyRangeIgnored(path, "bytes=-1, 9223372036854775808-");

        try (Http1ClientResponse response = testClient.get(path)
                .header(HeaderNames.RANGE, "bytes=-0")
                .request()) {

            assertThat(path + " status for bytes=-0",
                       response.status(), is(Status.REQUESTED_RANGE_NOT_SATISFIABLE_416));
        }

        try (Http1ClientResponse response = testClient.get(path)
                .header(HeaderNames.RANGE, "bytes=0-")
                .request()) {

            assertThat(path + " status for bytes=0-",
                       response.status(), is(Status.REQUESTED_RANGE_NOT_SATISFIABLE_416));
        }
    }

    private void assertEmptyRangeIgnored(String path, String range) {
        try (Http1ClientResponse response = testClient.get(path)
                .header(HeaderNames.RANGE, range)
                .request()) {

            String description = path + " with " + range;
            assertThat(description + " status", response.status(), is(Status.OK_200));
            assertThat(description + " content length",
                       response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.CONTENT_LENGTH, "0"));
            assertThat(description + " content range",
                       response.headers().contains(HeaderNames.CONTENT_RANGE), is(false));
            assertThat(description + " entity", response.entity().hasEntity(), is(false));
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

    private record TestEncoding() implements ContentEncoding {
        @Override
        public Set<String> ids() {
            return Set.of("gzip");
        }

        @Override
        public boolean supportsEncoding() {
            return true;
        }

        @Override
        public boolean supportsDecoding() {
            return false;
        }

        @Override
        public ContentDecoder decoder() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ContentEncoder encoder() {
            return new ContentEncoder() {
                @Override
                public OutputStream apply(OutputStream network) {
                    return new OutputStream() {
                        private boolean prefixWritten;

                        @Override
                        public void write(int b) throws IOException {
                            writePrefix();
                            network.write(b);
                        }

                        @Override
                        public void write(byte[] bytes, int offset, int length) throws IOException {
                            writePrefix();
                            network.write(bytes, offset, length);
                        }

                        @Override
                        public void flush() throws IOException {
                            network.flush();
                        }

                        @Override
                        public void close() throws IOException {
                            network.close();
                        }

                        private void writePrefix() throws IOException {
                            if (!prefixWritten) {
                                network.write("runtime:".getBytes(StandardCharsets.UTF_8));
                                prefixWritten = true;
                            }
                        }
                    };
                }

                @Override
                public void headers(WritableHeaders<?> headers) {
                    headers.add(HeaderValues.create(HeaderNames.CONTENT_ENCODING, "gzip"));
                    headers.remove(HeaderNames.CONTENT_LENGTH);
                }
            };
        }

        @Override
        public String name() {
            return "gzip";
        }

        @Override
        public String type() {
            return "gzip";
        }
    }

}
