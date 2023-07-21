package io.helidon.nima.webclient.api;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import io.helidon.common.http.Http;
import io.helidon.nima.common.tls.Tls;

/**
 * Client request with getters for all configurable options, used for integration with HTTP version implementations.
 */
public interface FullClientRequest<T extends ClientRequest<T>> extends ClientRequest<T> {
    /**
     * Replace a placeholder in URI with an actual value.
     *
     * @return a map of path parameters
     */
    Map<String, String> pathParams();

    /**
     * HTTP method of this request.
     *
     * @return method
     */
    Http.Method method();

    /**
     * URI of this request.
     *
     * @return client URI
     */
    ClientUri uri();

    /**
     * Configured properties.
     *
     * @return properties
     */
    Map<String, String> properties();

    /**
     * Request ID.
     *
     * @return id of this request
     */
    String requestId();

    /**
     * Possible explicit connection to use (such as when using a proxy).
     *
     * @return client connection if explicitly defined
     */
    Optional<ClientConnection> connection();

    /**
     * Read timeout.
     *
     * @return read timeout of this request
     */
    Duration readTimeout();

    /**
     * TLS configuration (may be disabled - e.g. use plaintext).
     *
     * @return TLS configuration
     */
    Tls tls();

    /**
     * Proxy configuration (may be no-proxy).
     *
     * @return proxy
     */
    Proxy proxy();

    /**
     * Whether to use keep-alive connection (if relevant for the used HTTP version).
     *
     * @return whether to use keep alive
     */
    boolean keepAlive();

    /**
     * Whether to skip URI encoding.
     *
     * @return whether to skip encoding
     */
    boolean skipUriEncoding();
}
