package io.helidon.nima.webclient.api;

import java.util.Map;
import java.util.Optional;

import io.helidon.builder.api.Prototype;
import io.helidon.common.http.Http;

/**
 * Configuration of each request.
 */
@Prototype.Blueprint(builderPublic = false)
interface ClientRequestConfigBlueprint extends HttpConfigBaseBlueprint {
    /**
     * HTTP method of this request.
     *
     * @return method
     */
    Http.Method method();

    /**
     * Possible explicit connection to use (such as when using a proxy).
     *
     * @return client connection if explicitly defined
     */
    Optional<ClientConnection> connection();

    /**
     * Replace a placeholder in URI with an actual value.
     *
     * @return a map of path parameters
     */
    @Prototype.Singular
    Map<String, String> pathParams();
}
