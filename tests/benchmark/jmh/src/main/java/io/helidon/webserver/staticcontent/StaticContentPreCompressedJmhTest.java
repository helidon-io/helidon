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
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.function.UnaryOperator;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import io.helidon.common.LruCache;
import io.helidon.common.context.Context;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.common.parameters.Parameters;
import io.helidon.common.socket.PeerInfo;
import io.helidon.common.uri.UriFragment;
import io.helidon.common.uri.UriInfo;
import io.helidon.common.uri.UriPath;
import io.helidon.common.uri.UriPathSegment;
import io.helidon.common.uri.UriQuery;
import io.helidon.http.Header;
import io.helidon.http.HeaderNames;
import io.helidon.http.HttpPrologue;
import io.helidon.http.Method;
import io.helidon.http.RoutedPath;
import io.helidon.http.ServerRequestHeaders;
import io.helidon.http.ServerResponseHeaders;
import io.helidon.http.ServerResponseTrailers;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;
import io.helidon.http.encoding.ContentDecoder;
import io.helidon.http.encoding.ContentEncoder;
import io.helidon.http.encoding.ContentEncoding;
import io.helidon.http.encoding.ContentEncodingContext;
import io.helidon.http.media.MediaContext;
import io.helidon.http.media.ReadableEntity;
import io.helidon.webserver.ListenerConfig;
import io.helidon.webserver.ListenerContext;
import io.helidon.webserver.ProxyProtocolData;
import io.helidon.webserver.http.DirectHandlers;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.HttpSecurity;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;

@State(Scope.Benchmark)
public class StaticContentPreCompressedJmhTest {
    private static final String RESOURCE = "resource.txt";
    private static final String CLASSPATH_RESOURCE = "benchmark/" + RESOURCE;

    private StaticContentHandler handler;
    private StaticContentHandler disabledHandler;
    private FileSystemContentHandler fileSystemMissingSidecarHandler;
    private FileSystemContentHandler disabledFileSystemMissingSidecarHandler;
    private ClassPathContentHandler classpathHandler;
    private ClassPathContentHandler disabledClasspathHandler;
    private ClassPathContentHandler classpathMemoryLookupHandler;
    private ClassPathContentHandler classpathRecordLookupHandler;
    private SidecarCache.Resolver sidecarResolver;
    private SidecarCache.Resolver fileSidecarResolver;
    private CachedHandler identityHandler;
    private CachedHandler fileIdentityHandler;
    private CachedHandler classpathIdentityHandler;
    private CachedHandler disabledClasspathIdentityHandler;
    private LruCache<String, CachedHandler> fileHandlerCache;
    private ServerRequest noAcceptEncodingRequest;
    private ServerRequest identityRequest;
    private ServerRequest identityPreferredRequest;
    private ServerRequest sidecarPreferredRequest;
    private ServerRequest brRequest;
    private ServerRequest gzipRequest;
    private ServerRequest runtimeGzipRequest;
    private Path benchmarkDirectory;
    private Path classpathJarPath;
    private Path identityPath;
    private Path sidecarPath;
    private URLClassLoader classpathClassLoader;

    @Setup
    public void setup() throws IOException, URISyntaxException {
        benchmarkDirectory = Files.createTempDirectory("static-content-pre-compressed-jmh-").toRealPath();
        identityPath = Files.writeString(benchmarkDirectory.resolve(RESOURCE),
                                         "Content",
                                         StandardCharsets.UTF_8).toRealPath();
        sidecarPath = Files.writeString(benchmarkDirectory.resolve(RESOURCE + ".br"),
                                        "Brotli content",
                                        StandardCharsets.UTF_8).toRealPath();
        classpathJarPath = benchmarkDirectory.resolve("classpath-resources.jar");
        try (var jarOutput = new JarOutputStream(Files.newOutputStream(classpathJarPath))) {
            jarOutput.putNextEntry(new JarEntry(CLASSPATH_RESOURCE));
            jarOutput.write("Content".getBytes(StandardCharsets.UTF_8));
            jarOutput.closeEntry();
        }
        classpathClassLoader = new URLClassLoader(new URL[] {classpathJarPath.toUri().toURL()}, null);
        URL classpathIdentityUrl = classpathClassLoader.getResource(CLASSPATH_RESOURCE);
        if (classpathIdentityUrl == null) {
            throw new IllegalStateException("Benchmark classpath resource was not found in the generated JAR");
        }

        handler = new BenchmarkStaticContentHandler(FileSystemHandlerConfig.builder()
                                                           .location(benchmarkDirectory)
                                                           .preCompressedEnabled(true)
                                                           .build());
        disabledHandler = new BenchmarkStaticContentHandler(FileSystemHandlerConfig.builder()
                                                                   .location(benchmarkDirectory)
                                                                   .preCompressedEnabled(false)
                                                                   .build());
        fileSystemMissingSidecarHandler = new FileSystemContentHandler(FileSystemHandlerConfig.builder()
                                                                               .location(benchmarkDirectory)
                                                                               .preCompressedEnabled(true)
                                                                               .build());
        disabledFileSystemMissingSidecarHandler = new FileSystemContentHandler(FileSystemHandlerConfig.builder()
                                                                                       .location(benchmarkDirectory)
                                                                                       .preCompressedEnabled(false)
                                                                                       .build());
        classpathHandler = new ClassPathContentHandler(ClasspathHandlerConfig.builder()
                                                               .location("benchmark")
                                                               .classLoader(classpathClassLoader)
                                                               .preCompressedEnabled(true)
                                                               .build());
        disabledClasspathHandler = new ClassPathContentHandler(ClasspathHandlerConfig.builder()
                                                                       .location("benchmark")
                                                                       .classLoader(classpathClassLoader)
                                                                       .preCompressedEnabled(false)
                                                                       .build());
        classpathMemoryLookupHandler = new ClassPathContentHandler(ClasspathHandlerConfig.builder()
                                                                           .location("benchmark")
                                                                           .classLoader(classpathClassLoader)
                                                                           .preCompressedEnabled(true)
                                                                           .build());
        classpathRecordLookupHandler = new ClassPathContentHandler(ClasspathHandlerConfig.builder()
                                                                           .location("benchmark")
                                                                           .classLoader(classpathClassLoader)
                                                                           .preCompressedEnabled(true)
                                                                           .build());
        identityHandler = inMemoryHandler("Content");
        CachedHandler brHandler = inMemoryHandler("Brotli content")
                .withRepresentation(ResponseRepresentation.encoded("br"));
        fileIdentityHandler = CachedHandlerPath.create(identityPath,
                                                       identityPath,
                                                       MediaTypes.TEXT_PLAIN,
                                                       false,
                                                       benchmarkDirectory);
        classpathIdentityHandler = new ClassPathContentHandler.CachedClassPathHandler(inMemoryHandler("Content"),
                                                                                      CLASSPATH_RESOURCE,
                                                                                      classpathIdentityUrl);
        disabledClasspathIdentityHandler =
                new ClassPathContentHandler.CachedClassPathHandler(inMemoryHandler("Content"),
                                                                   CLASSPATH_RESOURCE,
                                                                   classpathIdentityUrl);
        CachedHandlerInMemory classpathMemoryIdentityHandler = inMemoryHandler("Content");
        classpathMemoryLookupHandler.cacheInMemory(CLASSPATH_RESOURCE, classpathMemoryIdentityHandler);
        classpathMemoryLookupHandler.cacheClassPathHandler(CLASSPATH_RESOURCE,
                                                           CLASSPATH_RESOURCE,
                                                           classpathIdentityUrl,
                                                           classpathMemoryIdentityHandler);
        classpathRecordLookupHandler.cacheClassPathHandler(CLASSPATH_RESOURCE,
                                                           CLASSPATH_RESOURCE,
                                                           classpathIdentityUrl,
                                                           fileIdentityHandler);
        CachedHandler fileBrHandler = CachedHandlerPath.create(sidecarPath,
                                                               sidecarPath,
                                                               MediaTypes.TEXT_PLAIN,
                                                               false,
                                                               benchmarkDirectory)
                .withRepresentation(ResponseRepresentation.encoded("br"));

        sidecarResolver = (coding, suffix) -> "br".equals(coding) ? Optional.of(brHandler) : Optional.empty();
        fileSidecarResolver = (coding, suffix) -> "br".equals(coding) ? Optional.of(fileBrHandler) : Optional.empty();
        fileHandlerCache = LruCache.create();
        fileHandlerCache.put(RESOURCE, fileIdentityHandler);

        noAcceptEncodingRequest = request(null, ContentEncodingContext.create());
        identityRequest = request("identity", ContentEncodingContext.create());
        identityPreferredRequest = request("br;q=0.1, gzip;q=0.1", ContentEncodingContext.create());
        sidecarPreferredRequest = request("br, gzip;q=0.5, identity;q=0", ContentEncodingContext.create());
        brRequest = request("br", ContentEncodingContext.create());
        gzipRequest = request("gzip", ContentEncodingContext.create());
        runtimeGzipRequest = request("gzip, identity;q=0", runtimeContentEncodingContext());

        fileSystemMissingSidecarHandler.beforeStart();
        disabledFileSystemMissingSidecarHandler.beforeStart();
        warmFileSystemHandler(fileSystemMissingSidecarHandler);
        warmFileSystemHandler(disabledFileSystemMissingSidecarHandler);

        handler.selectHandler(identityHandler, brRequest, sidecarResolver);
        handler.selectHandler(identityHandler, gzipRequest, sidecarResolver);
        handler.selectHandler(fileIdentityHandler, brRequest, fileSidecarResolver);
        fileSidecarResolver = (coding, suffix) -> {
            throw new IllegalStateException("Cached filesystem sidecar was resolved again");
        };
    }

    @TearDown
    public void tearDown() throws IOException {
        fileSystemMissingSidecarHandler.afterStop();
        disabledFileSystemMissingSidecarHandler.afterStop();
        classpathClassLoader.close();
        Files.deleteIfExists(sidecarPath);
        Files.deleteIfExists(identityPath);
        Files.deleteIfExists(classpathJarPath);
        Files.deleteIfExists(benchmarkDirectory);
    }

    @Benchmark
    public CachedHandler noAcceptEncoding() throws IOException, URISyntaxException {
        return handler.selectHandler(identityHandler, noAcceptEncodingRequest, sidecarResolver);
    }

    @Benchmark
    public CachedHandler identityAccepted() throws IOException, URISyntaxException {
        return handler.selectHandler(identityHandler, identityRequest, sidecarResolver);
    }

    @Benchmark
    public CachedHandler identityPreferredOverSidecars() throws IOException, URISyntaxException {
        return handler.selectHandler(identityHandler, identityPreferredRequest, sidecarResolver);
    }

    @Benchmark
    public CachedHandler preferredSidecar() throws IOException, URISyntaxException {
        return handler.selectHandler(identityHandler, sidecarPreferredRequest, sidecarResolver);
    }

    @Benchmark
    public CachedHandler cachedSidecarHit() throws IOException, URISyntaxException {
        return handler.selectHandler(identityHandler, brRequest, sidecarResolver);
    }

    @Benchmark
    public CachedHandler cachedSidecarMiss() throws IOException, URISyntaxException {
        return handler.selectHandler(identityHandler, gzipRequest, sidecarResolver);
    }

    @Benchmark
    @Threads(4)
    public boolean fileSystemMissingSidecarEnabled(MissingSidecarRequestState state) throws IOException {
        return fileSystemMissingSidecarHandler.doHandle(Method.GET,
                                                        RESOURCE,
                                                        state.request,
                                                        new ResponseStub(),
                                                        true);
    }

    @Benchmark
    @Threads(4)
    public boolean fileSystemMissingSidecarDisabled(MissingSidecarRequestState state) throws IOException {
        return disabledFileSystemMissingSidecarHandler.doHandle(Method.GET,
                                                                RESOURCE,
                                                                state.request,
                                                                new ResponseStub(),
                                                                true);
    }

    @Benchmark
    @Threads(4)
    public CachedHandler classpathJarMissingSidecarEnabled(MissingSidecarRequestState state)
            throws IOException, URISyntaxException {
        return classpathHandler.selectCachedClassPathHandler(CLASSPATH_RESOURCE,
                                                             classpathIdentityHandler,
                                                             state.request);
    }

    @Benchmark
    @Threads(4)
    public CachedHandler classpathJarMissingSidecarDisabled(MissingSidecarRequestState state)
            throws IOException, URISyntaxException {
        return disabledClasspathHandler.selectCachedClassPathHandler(CLASSPATH_RESOURCE,
                                                                     disabledClasspathIdentityHandler,
                                                                     state.request);
    }

    @Benchmark
    @Threads(4)
    public Optional<CachedHandler> classpathMemoryMetadataLookup() {
        return classpathMemoryLookupHandler.cachedClassPathHandler(CLASSPATH_RESOURCE);
    }

    @Benchmark
    @Threads(4)
    public Optional<CachedHandler> classpathMemoryCacheLookupControl() {
        return classpathMemoryLookupHandler.cacheHandler(CLASSPATH_RESOURCE);
    }

    @Benchmark
    @Threads(4)
    public Optional<CachedHandler> classpathRecordMetadataLookup() {
        return classpathRecordLookupHandler.cachedClassPathHandler(CLASSPATH_RESOURCE);
    }

    @Benchmark
    @Threads(4)
    public Optional<CachedHandler> classpathRecordCacheLookupControl() {
        return classpathRecordLookupHandler.handlerCache().get(CLASSPATH_RESOURCE);
    }

    @Benchmark
    public CachedHandler runtimeFallback() throws IOException, URISyntaxException {
        return handler.selectHandler(identityHandler, runtimeGzipRequest, sidecarResolver);
    }

    @Benchmark
    public ServerResponse handleNoAcceptEncodingEnabled() throws IOException, URISyntaxException {
        return selectAndHandle(handler, noAcceptEncodingRequest);
    }

    @Benchmark
    public ServerResponse handleNoAcceptEncodingDisabled() throws IOException, URISyntaxException {
        return selectAndHandle(disabledHandler, noAcceptEncodingRequest);
    }

    @Benchmark
    public ServerResponse handleCachedFileSystemSidecar() throws IOException, URISyntaxException {
        return selectAndHandle(handler, brRequest);
    }

    private static CachedHandlerInMemory inMemoryHandler(String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        return new CachedHandlerInMemory(StaticContentMetadata.create(MediaTypes.TEXT_PLAIN, bytes.length), bytes);
    }

    private static ServerRequest request(String acceptEncoding, ContentEncodingContext contentEncodingContext) {
        WritableHeaders<?> headers = WritableHeaders.create();
        if (acceptEncoding != null) {
            headers.add(HeaderNames.ACCEPT_ENCODING, acceptEncoding);
        }
        return new RequestStub(ServerRequestHeaders.create(headers), new ListenerContextStub(contentEncodingContext));
    }

    private static ContentEncodingContext runtimeContentEncodingContext() {
        return ContentEncodingContext.builder()
                .addContentEncoding(new TestEncoding())
                .build();
    }

    private static UnsupportedOperationException unsupported() {
        return new UnsupportedOperationException("Not needed by this benchmark");
    }

    private ServerResponse selectAndHandle(StaticContentHandler staticContentHandler, ServerRequest request)
            throws IOException, URISyntaxException {
        CachedHandler selected = staticContentHandler.selectHandler(fileIdentityHandler, request, fileSidecarResolver);
        var response = new ResponseStub();
        if (!selected.handle(fileHandlerCache, Method.GET, request, response, RESOURCE)) {
            throw new IllegalStateException("Cached benchmark handler did not handle the resource");
        }
        return response;
    }

    private void warmFileSystemHandler(FileSystemContentHandler fileSystemContentHandler) throws IOException {
        boolean handled = fileSystemContentHandler.doHandle(Method.GET,
                                                            RESOURCE,
                                                            noAcceptEncodingRequest,
                                                            new ResponseStub(),
                                                            true);
        if (!handled) {
            throw new IllegalStateException("Filesystem benchmark handler did not handle the resource");
        }
    }

    @State(Scope.Thread)
    public static class MissingSidecarRequestState {
        private ServerRequest request;

        @Setup
        public void setup() {
            request = request("gzip, identity;q=0.5", ContentEncodingContext.create());
        }
    }

    private static final class BenchmarkStaticContentHandler extends StaticContentHandler {
        private BenchmarkStaticContentHandler(BaseHandlerConfig config) {
            super(config);
        }

        @Override
        public void routing(HttpRules rules) {
            throw unsupported();
        }

        @Override
        boolean doHandle(Method method,
                         String requestedPath,
                         ServerRequest request,
                         ServerResponse response,
                         boolean mapped) {
            throw unsupported();
        }
    }

    private static final class ResponseStub implements ServerResponse {
        private final ServerResponseHeaders headers = ServerResponseHeaders.create();
        private final OutputStream output = new OutputStream() {
            @Override
            public void write(int value) {
                bytesWritten++;
            }

            @Override
            public void write(byte[] bytes, int offset, int length) {
                bytesWritten += length;
            }
        };
        private Status status = Status.OK_200;
        private boolean sent;
        private long bytesWritten;

        @Override
        public ServerResponse status(Status status) {
            this.status = status;
            return this;
        }

        @Override
        public Status status() {
            return status;
        }

        @Override
        public ServerResponse header(Header header) {
            headers.set(header);
            return this;
        }

        @Override
        public void send() {
            sent = true;
        }

        @Override
        public void send(byte[] bytes) {
            bytesWritten += bytes.length;
            sent = true;
        }

        @Override
        public void send(Object entity) {
            throw unsupported();
        }

        @Override
        public boolean isSent() {
            return sent;
        }

        @Override
        public OutputStream outputStream() {
            sent = true;
            return output;
        }

        @Override
        public long bytesWritten() {
            return bytesWritten;
        }

        @Override
        public ServerResponse whenSent(Runnable listener) {
            listener.run();
            return this;
        }

        @Override
        public ServerResponse reroute(String newPath) {
            throw unsupported();
        }

        @Override
        public ServerResponse reroute(String path, UriQuery query) {
            throw unsupported();
        }

        @Override
        public ServerResponse next() {
            throw unsupported();
        }

        @Override
        public ServerResponseHeaders headers() {
            return headers;
        }

        @Override
        public ServerResponseTrailers trailers() {
            throw unsupported();
        }

        @Override
        public void streamResult(String result) {
            throw unsupported();
        }

        @Override
        public void streamFilter(UnaryOperator<OutputStream> filterFunction) {
            throw unsupported();
        }
    }

    private record RequestStub(ServerRequestHeaders headers, ListenerContext listenerContext) implements ServerRequest {
        @Override
        public void reset() {
            throw unsupported();
        }

        @Override
        public boolean isSecure() {
            throw unsupported();
        }

        @Override
        public RoutedPath path() {
            return RoutedPathStub.INSTANCE;
        }

        @Override
        public ReadableEntity content() {
            throw unsupported();
        }

        @Override
        public String socketId() {
            throw unsupported();
        }

        @Override
        public String serverSocketId() {
            throw unsupported();
        }

        @Override
        public Context context() {
            throw unsupported();
        }

        @Override
        public HttpSecurity security() {
            throw unsupported();
        }

        @Override
        public boolean continueSent() {
            throw unsupported();
        }

        @Override
        public void streamFilter(UnaryOperator<InputStream> filterFunction) {
            throw unsupported();
        }

        @Override
        public Optional<ProxyProtocolData> proxyProtocolData() {
            return Optional.empty();
        }

        @Override
        public HttpPrologue prologue() {
            return HttpPrologue.create("HTTP/1.1",
                                       "HTTP",
                                       "1.1",
                                       Method.GET,
                                       UriPath.create("/" + RESOURCE),
                                       UriQuery.empty(),
                                       UriFragment.empty());
        }

        @Override
        public UriQuery query() {
            throw unsupported();
        }

        @Override
        public PeerInfo remotePeer() {
            throw unsupported();
        }

        @Override
        public PeerInfo localPeer() {
            throw unsupported();
        }

        @Override
        public String authority() {
            throw unsupported();
        }

        @Override
        public void header(Header header) {
            throw unsupported();
        }

        @Override
        public int id() {
            throw unsupported();
        }

        @Override
        public UriInfo requestedUri() {
            throw unsupported();
        }
    }

    private enum RoutedPathStub implements RoutedPath {
        INSTANCE;

        private final UriPath delegate = UriPath.create("/" + RESOURCE);

        @Override
        public Parameters pathParameters() {
            return Parameters.empty("benchmark");
        }

        @Override
        public RoutedPath absolute() {
            return this;
        }

        @Override
        public String rawPath() {
            return delegate.rawPath();
        }

        @Override
        public String rawPathNoParams() {
            return delegate.rawPathNoParams();
        }

        @Override
        public String path() {
            return delegate.path();
        }

        @Override
        public Parameters matrixParameters() {
            return delegate.matrixParameters();
        }

        @Override
        public List<UriPathSegment> segments() {
            return delegate.segments();
        }

        @Override
        public void validate() {
            delegate.validate();
        }
    }

    private record ListenerContextStub(ContentEncodingContext contentEncodingContext) implements ListenerContext {
        @Override
        public Context context() {
            throw unsupported();
        }

        @Override
        public MediaContext mediaContext() {
            throw unsupported();
        }

        @Override
        public DirectHandlers directHandlers() {
            throw unsupported();
        }

        @Override
        public ListenerConfig config() {
            throw unsupported();
        }

        @Override
        public ExecutorService executor() {
            throw unsupported();
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
            throw unsupported();
        }

        @Override
        public ContentEncoder encoder() {
            return new ContentEncoder() {
                @Override
                public OutputStream apply(OutputStream network) {
                    return network;
                }

                @Override
                public void headers(WritableHeaders<?> headers) {
                    headers.set(HeaderNames.CONTENT_ENCODING, "gzip");
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
