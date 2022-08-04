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
package io.helidon.webserver;

import java.util.List;

import io.helidon.common.http.Http;

/**
 * A route for HTTP/1.1 only.
 * To create a route valid for any version of HTTP, please use {@link io.helidon.webserver.HttpRoute}.
 */
public class Http1Route extends HandlerRoute implements HttpRoute {

    protected Http1Route(Http.Method method, String path, Handler handler) {
        super(List.of(), PathMatcher.create(path), handler, method);
    }
    protected Http1Route(PathMatcher pathMatcher, Handler handler, Http.Method... methods) {
        super(List.of(), pathMatcher, handler, methods);
    }

    @Override
    public boolean matchVersion(Http.Version version) {
        return switch (version) {
            case V1_0, V1_1 -> true;
            default -> false;
        };
    }

    /**
     * Create an HTTP/1 specific route.
     *
     * @param method  accepted method
     * @param path    path pattern
     * @param handler handler
     * @return a new HTTP/1.1 specific route
     */
    public static Http1Route route(Http.Method method, String path, Handler handler) {
        return new Http1Route(method, path, handler);
    }

    /**
     * Create an HTTP/1.1 specific route.
     *
     * @param pathMatcher URI Path Matcher
     * @param handler handler
     * @param methods HTTP {@link io.helidon.common.http.Http.RequestMethod methods} handled by this route
     * @return a new HTTP/1.1 specific route
     */
    public static Http1Route route(PathMatcher pathMatcher, Handler handler, Http.Method... methods) {
        return new Http1Route(pathMatcher, handler, methods);
    }

}
