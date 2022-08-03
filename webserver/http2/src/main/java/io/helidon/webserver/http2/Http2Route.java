/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.webserver.http2;

import io.helidon.common.http.Http;
import io.helidon.webserver.Handler;
import io.helidon.webserver.Http1Route;
import io.helidon.webserver.PathMatcher;

/**
 * A route for HTTP/2 only.
 * To create a route valid for any version of HTTP, please use {@link io.helidon.webserver.HttpRoute}.
 */
public class Http2Route extends Http1Route {

    private Http2Route(Http.Method method, String path, Handler handler) {
        super(method, path, handler);
    }

    private Http2Route(PathMatcher pathMatcher, Handler handler, Http.Method... methods) {
        super(pathMatcher, handler, methods);
    }

    @Override
    public boolean matchVersion(Http.Version version) {
        return Http.Version.V2_0 == version;
    }

    /**
     * Create an HTTP/2 specific route.
     *
     * @param method  accepted method
     * @param path    path pattern
     * @param handler handler
     * @return a new HTTP/2.0 specific route
     */
    public static Http2Route route(Http.Method method, String path, Handler handler) {
        return new Http2Route(method, path, handler);
    }

    /**
     * Create an HTTP/2 specific route.
     *
     * @param pathMatcher URI Path Matcher
     * @param handler handler
     * @param methods HTTP {@link io.helidon.common.http.Http.RequestMethod methods} handled by this route
     * @return a new HTTP/2 specific route
     */
    public static Http2Route route(PathMatcher pathMatcher, Handler handler, Http.Method... methods) {
        return new Http2Route(pathMatcher, handler, methods);
    }
}
