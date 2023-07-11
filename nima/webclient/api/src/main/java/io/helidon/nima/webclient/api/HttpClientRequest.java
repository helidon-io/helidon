package io.helidon.nima.webclient.api;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import io.helidon.common.http.ClientRequestHeaders;
import io.helidon.common.http.Headers;
import io.helidon.common.http.Http;
import io.helidon.common.uri.UriFragment;
import io.helidon.common.uri.UriQueryWriteable;
import io.helidon.nima.common.tls.Tls;
import io.helidon.nima.webclient.spi.HttpClientSpi;

public class HttpClientRequest implements ClientRequest<HttpClientRequest> {
    private final ClientRequestConfig.Builder clientRequest = new ClientRequestConfig.Builder();
    private final UriQueryWriteable query;
    private final ClientRequestHeaders headers;
    private final ClientUri clientUri;
    private final List<HttpClientSpi> clients;

    private UriFragment fragment;
    private ClientRequest<?> delegate;

    HttpClientRequest(WebClientConfig clientConfig, Http.Method method, List<HttpClientSpi> clients) {
        this.clients = clients;
        this.clientRequest.from(clientConfig);
        this.clientRequest.method(method);
        this.headers = clientConfig.defaultRequestHeaders();
        this.query = UriQueryWriteable.create();
        clientConfig.baseQuery().ifPresent(this.query::from);
        this.fragment = clientConfig.baseFragment().orElse(null);
        this.clientUri = clientConfig.baseUri()
                .map(ClientUri::create) // create from base config
                .orElseGet(ClientUri::create); // create as empty
    }

    @Override
    public HttpClientRequest tls(Tls tls) {
        clientRequest.tls(tls);
        return this;
    }

    @Override
    public HttpClientRequest proxy(Proxy proxy) {
        clientRequest.proxy(proxy);
        return this;
    }

    @Override
    public HttpClientRequest uri(URI uri) {
        this.clientUri.resolve(uri, query);
        return this;
    }

    @Override
    public HttpClientRequest header(Http.HeaderValue header) {
        headers.set(header);
        return this;
    }

    @Override
    public HttpClientRequest headers(Headers headers) {
        headers.forEach(this.headers::set);
        return this;
    }

    @Override
    public HttpClientRequest headers(Consumer<ClientRequestHeaders> headersConsumer) {
        headersConsumer.accept(this.headers);
        return this;
    }

    @Override
    public HttpClientRequest pathParam(String name, String value) {
        clientRequest.putPathParam(name, value);
        return this;
    }

    @Override
    public HttpClientRequest queryParam(String name, String... values) {
        this.query.set(name, values);
        return this;
    }

    @Override
    public HttpClientRequest fragment(UriFragment fragment) {
        this.fragment = fragment;
        return this;
    }

    @Override
    public HttpClientRequest followRedirects(boolean followRedirects) {
        this.clientRequest.followRedirects(followRedirects);
        return this;
    }

    @Override
    public HttpClientRequest maxRedirects(int maxRedirects) {
        this.clientRequest.maxRedirects(maxRedirects);
        return this;
    }

    @Override
    public ClientRequestHeaders headers() {
        return headers;
    }

    @Override
    public HttpClientRequest skipUriEncoding(boolean skip) {
        clientUri.skipUriEncoding(skip);
        return this;
    }

    @Override
    public HttpClientRequest property(String propertyName, String propertyValue) {
        clientRequest.putProperty(propertyName, propertyValue);
        return this;
    }

    @Override
    public HttpClientRequest keepAlive(boolean keepAlive) {
        clientRequest.keepAlive(keepAlive);
        return this;
    }

    @Override
    public HttpClientRequest connection(ClientConnection connection) {
        clientRequest.connection(connection);
        return this;
    }

    @Override
    public HttpClientRequest readTimeout(Duration readTimeout) {
        clientRequest.readTimeout(readTimeout);
        return this;
    }

    /**
     * This method is only correctly resolved after the request across the network is done.
     *
     * @return resolved URI (post redirects)
     */
    @Override
    public ClientUri resolvedUri() {
        if (delegate == null) {
            return clientUri;
        }
        return delegate.resolvedUri();
    }

    /*
    These are terminating methods - these actually do a request across the network
     */

    @Override
    public HttpClientResponse submit(Object entity) {
        for (HttpClientSpi client : clients) {
            Optional<ClientRequest<?>> maybeRequest = client.clientRequest(clientRequest.build(),
                                                                           clientUri,
                                                                           headers,
                                                                           query,
                                                                           fragment);
            if (maybeRequest.isPresent()) {
                this.delegate = maybeRequest.get();
                return delegate.submit(entity);
            }
        }

        throw new IllegalStateException("There was no HTTP client capable of handling this requests, clients: " + clients);
    }

    @Override
    public HttpClientResponse outputStream(OutputStreamHandler outputStreamConsumer) {
        for (HttpClientSpi client : clients) {
            Optional<ClientRequest<?>> maybeRequest = client.clientRequest(clientRequest.build(),
                                                                           clientUri,
                                                                           headers,
                                                                           query,
                                                                           fragment);
            if (maybeRequest.isPresent()) {
                this.delegate = maybeRequest.get();
                return delegate.outputStream(outputStreamConsumer);
            }
        }

        throw new IllegalStateException("There was no HTTP client capable of handling this requests, clients: " + clients);
    }
}
