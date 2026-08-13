/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.http.Header;
import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.HttpException;
import io.helidon.http.ServerRequestHeaders;
import io.helidon.http.ServerResponseHeaders;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.infra.Blackhole;

@BenchmarkMode({Mode.AverageTime, Mode.SampleTime})
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
public class StaticContentMetadataBenchmark {
    private static final int FILE_SIZE = 4_096;
    private static final Instant REQUESTED_LAST_MODIFIED = Instant.parse("2026-08-13T08:00:00.123Z");

    private Path root;
    private Path path;
    private Path realRoot;
    private Instant lastModified;
    private long contentLength;
    private StaticContentMetadata afterMetadata;
    private Header beforeInMemoryContentLength;
    private BiConsumer<ServerResponseHeaders, Instant> setBeforeInMemoryLastModified;
    private ServerRequestHeaders noConditionalHeader;
    private ServerRequestHeaders beforeMatchingIfNoneMatch;
    private ServerRequestHeaders afterMatchingIfNoneMatch;

    @Setup(Level.Trial)
    public void setup() throws IOException {
        Path target = Path.of("target");
        Files.createDirectories(target);
        root = Files.createTempDirectory(target, "static-content-metadata-").toAbsolutePath().normalize();
        path = Files.write(root.resolve("resource.txt"), new byte[FILE_SIZE]);
        Files.setLastModifiedTime(path, FileTime.from(REQUESTED_LAST_MODIFIED));
        realRoot = root.toRealPath();

        BasicFileAttributes attributes = FileBasedContentHandler.attributes(path, false, realRoot);
        lastModified = attributes.lastModifiedTime().toInstant();
        contentLength = attributes.size();
        String beforeEtag = String.valueOf(lastModified.toEpochMilli());
        afterMetadata = StaticContentMetadata.create(MediaTypes.TEXT_PLAIN, lastModified, contentLength);

        beforeInMemoryContentLength = HeaderValues.create(HeaderNames.CONTENT_LENGTH, contentLength);
        Header beforeInMemoryLastModified = HeaderValues.create(HeaderNames.LAST_MODIFIED,
                                                                true,
                                                                false,
                                                                StaticContentHandler.formatLastModified(lastModified));
        setBeforeInMemoryLastModified = (headers, _) -> headers.set(beforeInMemoryLastModified);

        noConditionalHeader = ServerRequestHeaders.create();
        beforeMatchingIfNoneMatch = requestHeaders(HeaderNames.IF_NONE_MATCH, '"' + beforeEtag + '"');
        afterMatchingIfNoneMatch = requestHeaders(HeaderNames.IF_NONE_MATCH, '"' + afterMetadata.etag() + '"');
    }

    @TearDown(Level.Trial)
    public void tearDown() throws IOException {
        Files.deleteIfExists(path);
        Files.deleteIfExists(root);
    }

    @Benchmark
    public ServerResponseHeaders fileSystemBeforeUnconditionalHead() throws IOException {
        ServerResponseHeaders responseHeaders = ServerResponseHeaders.create();
        fileSystemBefore(noConditionalHeader, responseHeaders);
        return responseHeaders;
    }

    @Benchmark
    public ServerResponseHeaders fileSystemAfterUnconditionalHead() {
        ServerResponseHeaders responseHeaders = ServerResponseHeaders.create();
        fileSystemAfter(noConditionalHeader, responseHeaders);
        return responseHeaders;
    }

    @Benchmark
    public void fileSystemBeforeMatchingIfNoneMatch(Blackhole blackhole) throws IOException {
        ServerResponseHeaders responseHeaders = ServerResponseHeaders.create();
        try {
            fileSystemBefore(beforeMatchingIfNoneMatch, responseHeaders);
            throw new AssertionError("Expected 304 response");
        } catch (HttpException e) {
            consumeNotModified(responseHeaders, e, blackhole);
        }
    }

    @Benchmark
    public void fileSystemAfterMatchingIfNoneMatch(Blackhole blackhole) {
        ServerResponseHeaders responseHeaders = ServerResponseHeaders.create();
        try {
            fileSystemAfter(afterMatchingIfNoneMatch, responseHeaders);
            throw new AssertionError("Expected 304 response");
        } catch (HttpException e) {
            consumeNotModified(responseHeaders, e, blackhole);
        }
    }

    @Benchmark
    public ServerResponseHeaders inMemoryBeforeUnconditionalHead() {
        ServerResponseHeaders responseHeaders = ServerResponseHeaders.create();
        inMemoryBefore(noConditionalHeader, responseHeaders);
        return responseHeaders;
    }

    @Benchmark
    public ServerResponseHeaders inMemoryAfterUnconditionalHead() {
        ServerResponseHeaders responseHeaders = ServerResponseHeaders.create();
        metadataAfter(noConditionalHeader, responseHeaders);
        return responseHeaders;
    }

    @Benchmark
    public void inMemoryBeforeMatchingIfNoneMatch(Blackhole blackhole) {
        ServerResponseHeaders responseHeaders = ServerResponseHeaders.create();
        try {
            inMemoryBefore(beforeMatchingIfNoneMatch, responseHeaders);
            throw new AssertionError("Expected 304 response");
        } catch (HttpException e) {
            consumeNotModified(responseHeaders, e, blackhole);
        }
    }

    @Benchmark
    public void inMemoryAfterMatchingIfNoneMatch(Blackhole blackhole) {
        ServerResponseHeaders responseHeaders = ServerResponseHeaders.create();
        try {
            metadataAfter(afterMatchingIfNoneMatch, responseHeaders);
            throw new AssertionError("Expected 304 response");
        } catch (HttpException e) {
            consumeNotModified(responseHeaders, e, blackhole);
        }
    }

    private void fileSystemBefore(ServerRequestHeaders requestHeaders,
                                  ServerResponseHeaders responseHeaders) throws IOException {
        Path expectedPath = realRoot.resolve(root.relativize(path)).normalize();
        Path expectedRealPath = expectedPath.toRealPath();
        Path resolvedPath = path.toRealPath();
        if (!path.startsWith(root)
                || !Files.exists(path)
                || !expectedRealPath.startsWith(realRoot)
                || !resolvedPath.equals(expectedRealPath)) {
            throw new IllegalStateException("Benchmark file is not within its configured root");
        }

        BasicFileAttributes attributes = FileBasedContentHandler.attributes(resolvedPath, false, realRoot);
        if (!attributes.isRegularFile()
                || !Files.isReadable(resolvedPath)
                || Files.isHidden(path)
                || Files.isHidden(resolvedPath)) {
            throw new IllegalStateException("Benchmark file is not accessible");
        }

        Instant modified = FileBasedContentHandler.lastModified(resolvedPath, false, realRoot).orElse(null);
        StaticContentHandler.processPreconditions(modified == null ? null : String.valueOf(modified.toEpochMilli()),
                                                  modified,
                                                  requestHeaders,
                                                  responseHeaders);
        responseHeaders.contentType(MediaTypes.TEXT_PLAIN);
        try (SeekableByteChannel channel = FileBasedContentHandler.newByteChannel(resolvedPath, false, realRoot)) {
            responseHeaders.set(HeaderValues.create(HeaderNames.CONTENT_LENGTH, channel.size()));
        }
    }

    private void inMemoryBefore(ServerRequestHeaders requestHeaders, ServerResponseHeaders responseHeaders) {
        StaticContentHandler.processPreconditions(lastModified == null ? null : String.valueOf(lastModified.toEpochMilli()),
                                                  lastModified,
                                                  requestHeaders,
                                                  responseHeaders,
                                                  setBeforeInMemoryLastModified);
        responseHeaders.contentType(MediaTypes.TEXT_PLAIN);
        responseHeaders.set(beforeInMemoryContentLength);
    }

    private void fileSystemAfter(ServerRequestHeaders requestHeaders, ServerResponseHeaders responseHeaders) {
        if (!Files.exists(path)) {
            throw new IllegalStateException("Benchmark file no longer exists");
        }
        metadataAfter(requestHeaders, responseHeaders);
    }

    private void metadataAfter(ServerRequestHeaders requestHeaders, ServerResponseHeaders responseHeaders) {
        StaticContentHandler.processPreconditions(afterMetadata, requestHeaders, responseHeaders);
        afterMetadata.setContentType(responseHeaders);
        afterMetadata.setContentLength(responseHeaders);
    }

    private static ServerRequestHeaders requestHeaders(HeaderName headerName, String value) {
        WritableHeaders<?> headers = WritableHeaders.create();
        headers.set(HeaderValues.create(headerName, value));
        return ServerRequestHeaders.create(headers);
    }

    private static void consumeNotModified(ServerResponseHeaders responseHeaders,
                                           HttpException exception,
                                           Blackhole blackhole) {
        if (exception.status() != Status.NOT_MODIFIED_304) {
            throw exception;
        }
        blackhole.consume(responseHeaders);
        blackhole.consume(exception);
    }
}
