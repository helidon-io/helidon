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
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.function.UnaryOperator;

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
import io.helidon.http.HeaderValues;
import io.helidon.http.HttpPrologue;
import io.helidon.http.Method;
import io.helidon.http.RoutedPath;
import io.helidon.http.ServerRequestHeaders;
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

@State(Scope.Benchmark)
public class StaticContentPreCompressedJmhTest {
    private static final String RESOURCE = "resource.txt";

    private StaticContentHandler handler;
    private SidecarCache.Resolver sidecarResolver;
    private CachedHandler identityHandler;
    private ServerRequest noAcceptEncodingRequest;
    private ServerRequest identityRequest;
    private ServerRequest brRequest;
    private ServerRequest gzipRequest;
    private ServerRequest runtimeGzipRequest;

    @Setup
    public void setup() throws IOException, URISyntaxException {
        handler = new BenchmarkStaticContentHandler();
        identityHandler = inMemoryHandler("Content");
        CachedHandler brHandler = inMemoryHandler("Brotli content")
                .withRepresentation(ResponseRepresentation.encoded("br"));

        sidecarResolver = (coding, suffix) -> "br".equals(coding) ? Optional.of(brHandler) : Optional.empty();

        noAcceptEncodingRequest = request(null, ContentEncodingContext.create());
        identityRequest = request("identity", ContentEncodingContext.create());
        brRequest = request("br", ContentEncodingContext.create());
        gzipRequest = request("gzip", ContentEncodingContext.create());
        runtimeGzipRequest = request("gzip, identity;q=0", runtimeContentEncodingContext());

        handler.selectHandler(identityHandler, brRequest, sidecarResolver);
        handler.selectHandler(identityHandler, gzipRequest, sidecarResolver);
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
    public CachedHandler cachedSidecarHit() throws IOException, URISyntaxException {
        return handler.selectHandler(identityHandler, brRequest, sidecarResolver);
    }

    @Benchmark
    public CachedHandler cachedSidecarMiss() throws IOException, URISyntaxException {
        return handler.selectHandler(identityHandler, gzipRequest, sidecarResolver);
    }

    @Benchmark
    public CachedHandler runtimeFallback() throws IOException, URISyntaxException {
        return handler.selectHandler(identityHandler, runtimeGzipRequest, sidecarResolver);
    }

    private static CachedHandlerInMemory inMemoryHandler(String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        return new CachedHandlerInMemory(MediaTypes.TEXT_PLAIN,
                                         null,
                                         null,
                                         bytes,
                                         bytes.length,
                                         HeaderValues.create(HeaderNames.CONTENT_LENGTH, bytes.length));
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

    private static final class BenchmarkStaticContentHandler extends StaticContentHandler {
        private BenchmarkStaticContentHandler() {
            super(FileSystemHandlerConfig.builder()
                          .location(Path.of("."))
                          .build());
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
        public java.util.List<UriPathSegment> segments() {
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
