package io.helidon.nima.http2.webclient;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import io.helidon.common.GenericType;
import io.helidon.common.buffers.BufferData;
import io.helidon.common.http.Http;
import io.helidon.common.http.WritableHeaders;
import io.helidon.common.socket.SocketOptions;
import io.helidon.common.uri.UriQueryWriteable;
import io.helidon.nima.http.media.EntityWriter;
import io.helidon.nima.http2.Http2Headers;
import io.helidon.nima.webclient.api.ClientConnection;
import io.helidon.nima.webclient.api.ClientUri;
import io.helidon.nima.webclient.api.ConnectionKey;
import io.helidon.nima.webclient.api.HttpClientConfig;
import io.helidon.nima.webclient.api.WebClient;
import io.helidon.nima.webclient.api.WebClientServiceRequest;
import io.helidon.nima.webclient.api.WebClientServiceResponse;

import static io.helidon.nima.webclient.api.ClientRequestBase.USER_AGENT_HEADER;

public class Http2CallEntityChain extends Http2CallChainBase {
    public Http2CallEntityChain(WebClient webClient,
                                Http2ClientRequestImpl request,
                                HttpClientConfig clientConfig,
                                Http2ClientProtocolConfig protocolConfig,
                                ClientConnection clientConnection,
                                CompletableFuture<WebClientServiceRequest> whenSent,
                                CompletableFuture<WebClientServiceResponse> whenComplete,
                                Object entity) {
        super(webClient, clientConfig, protocolConfig, request);
    }

    @Override
    public WebClientServiceResponse proceed(WebClientServiceRequest clientRequest) {
        WritableHeaders<?> headers = WritableHeaders.create(explicitHeaders);

        Http2ClientStream stream = reserveStream();

        byte[] entityBytes;
        if (entity == BufferData.EMPTY_BYTES) {
            entityBytes = BufferData.EMPTY_BYTES;
        } else {
            entityBytes = entityBytes(entity);
        }
        headers.set(Http.Header.create(Http.Header.CONTENT_LENGTH, entityBytes.length));
        headers.setIfAbsent(USER_AGENT_HEADER);

        Http2Headers http2Headers = prepareHeaders(headers);
        stream.write(http2Headers, entityBytes.length == 0);

        stream.flowControl().inbound().incrementWindowSize(requestPrefetch);

        if (entityBytes.length != 0) {
            stream.writeData(BufferData.create(entityBytes), true);
        }

        return readResponse(stream);
    }

    private Http2ClientResponse readResponse(Http2ClientStream stream) {
        Http2Headers headers = stream.readHeaders();

        return new Http2ClientResponseImpl(headers,
                                           Http2Headers.create(serviceResponse.serviceRequest().headers()),
                                           stream,
                                           serviceResponse.reader(),
                                           mediaContext(),
                                           clientConfig().mediaTypeParserMode(),
                                           clientUri,
                                           complete);
    }

    private byte[] entityBytes(Object entity) {
        if (entity instanceof byte[] bytes) {
            return bytes;
        }

        GenericType<Object> genericType = GenericType.create(entity);
        EntityWriter<Object> writer = mediaContext.writer(genericType, explicitHeaders);

        // This uses an in-memory buffer, which would cause damage for writing big objects (such as Path)
        // we have a follow-up issue to make sure this is fixed
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        writer.write(genericType, entity, bos, explicitHeaders);
        return bos.toByteArray();
    }

    private Http2Headers prepareHeaders(WritableHeaders<?> headers) {
        Http2Headers http2Headers = Http2Headers.create(headers);
        http2Headers.method(this.method);
        http2Headers.authority(this.clientUri.authority());
        http2Headers.scheme(this.clientUri.scheme());
        http2Headers.path(this.clientUri.pathWithQueryAndFragment(query, fragment));

        headers.remove(Http.Header.HOST, Http2ClientRequestImpl.LogHeaderConsumer.INSTANCE);
        headers.remove(Http.Header.TRANSFER_ENCODING, Http2ClientRequestImpl.LogHeaderConsumer.INSTANCE);
        headers.remove(Http.Header.CONNECTION, Http2ClientRequestImpl.LogHeaderConsumer.INSTANCE);
        return http2Headers;
    }

    private Http2ClientStream reserveStream() {
        if (explicitConnection == null) {
            return newStream(clientUri);
        } else {
            throw new UnsupportedOperationException("Explicit connection not (yet) supported for HTTP/2 client");
        }
    }

    private Http2ClientStream newStream(ClientUri uri) {
        try {
            ConnectionKey connectionKey = new ConnectionKey(method,
                                                            uri.scheme(),
                                                            uri.host(),
                                                            uri.port(),
                                                            priorKnowledge,
                                                            tls,
                                                            clientConfig.dnsResolver(),
                                                            clientConfig.dnsAddressLookup());

            // this statement locks all threads - must not do anything complicated (just create a new instance)
            return CHANNEL_CACHE.computeIfAbsent(connectionKey,
                                                 key -> new Http2ClientConnectionHandler(
                                                         SocketOptions.builder().build(),
                                                                                         uri.path(),
                                                                                         key))
                    // this statement may block a single connection key
                    .newStream(new ConnectionContext(priority,
                                                     priorKnowledge,
                                                     initialWindowSize,
                                                     maxFrameSize,
                                                     maxHeaderListSize,
                                                     connectionPrefetch,
                                                     requestPrefetch,
                                                     flowControlTimeout,
                                                     timeout));
        } catch (UpgradeRedirectException e) {
            return newStream(ClientUri.create(URI.create(e.redirectUri()), UriQueryWriteable.create()));
        }
    }

    private static final class LogHeaderConsumer implements Consumer<Http.HeaderValue> {
        private static final System.Logger LOGGER = System.getLogger(LogHeaderConsumer.class.getName());
        private static final LogHeaderConsumer INSTANCE = new LogHeaderConsumer();

        @Override
        public void accept(Http.HeaderValue httpHeader) {
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                LOGGER.log(System.Logger.Level.DEBUG,
                           "HTTP/2 request contains wrong header, removing: " + httpHeader);
            }
        }
    }
}
