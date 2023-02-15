package io.helidon.nima.webserver.http;

import io.helidon.common.http.Http;

/**
 * This class is only used by generated code.
 *
 * @deprecated please do not use directly, designed for generated code
 * @see io.helidon.nima.webserver.http1.Http1Route
 * @see io.helidon.nima.webserver.http.Handler
 */
@Deprecated(since = "4.0.0")
public interface GeneratedHandler extends Handler {
    /**
     * HTTP Method of this handler.
     *
     * @return method
     */
    Http.Method method();

    /**
     * Path this handler should be registered at.
     *
     * @return path, may include path parameter (template)
     */
    String path();


}
