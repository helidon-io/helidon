/*
 * Copyright (c) 2017, 2021 Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import io.helidon.common.http.Http;

import static io.helidon.webserver.PathHelper.extractPathParams;

/**
 * Represents a single routable {@link Handler} in the {@link Routing}.
 */
class HandlerRoute implements Route {

    private final PathMatcher pathMatcher;
    private final Handler handler;
    private final List<Service> serviceContext;
    private final HttpMethodPredicate methodPredicate;

    private final Map<String, String> diagnosticEvent;

    /**
     * Creates new instance.
     *
     * @param serviceContext a list of services which defines this route
     * @param pathMatcher    an effective path matcher. If {@code null} then {@link #EMPTY_PATH_MATCHER} is used
     * @param handler        an effective handler for the matcher
     * @param methods        accepted methods. If empty then all methods are accepted
     */
    HandlerRoute(List<Service> serviceContext, PathMatcher pathMatcher, Handler handler, Iterable<Http.RequestMethod> methods) {
        if (serviceContext == null || serviceContext.isEmpty()) {
            this.serviceContext = Collections.emptyList();
        } else {
            this.serviceContext = new ArrayList<>(serviceContext);
        }
        if (methods == null) {
            this.methodPredicate = new HttpMethodPredicate(null);
        } else if (methods instanceof Collection) {
            this.methodPredicate = new HttpMethodPredicate((Collection) methods);
        } else {
            Collection<Http.RequestMethod> mtds = new ArrayList<>();
            for (Http.RequestMethod method : methods) {
                mtds.add(method);
            }
            this.methodPredicate = new HttpMethodPredicate(mtds);
        }
        this.pathMatcher = pathMatcher == null ? EMPTY_PATH_MATCHER : pathMatcher;
        this.handler = handler;
        // Construct diagnostic event
        Map<String, String> eventData = new HashMap<>(5);
        eventData.put("event", "handler");
        if (this.handler != null) {
            eventData.put("handler.class", this.handler.getClass().getName());
        }
        if (!this.serviceContext.isEmpty()) {
            eventData.put("service.context.classes", this.serviceContext.stream()
                                                                        .map(s -> s.getClass().getName())
                                                                        .collect(Collectors.joining(" ")));
        }
        this.diagnosticEvent = Collections.unmodifiableMap(eventData);
    }

    /**
     * Creates new instance.
     *
     * @param serviceContext a list of services which defines this route
     * @param pathMatcher an effective path matcher. If {@code null} then {@link #EMPTY_PATH_MATCHER} is used
     * @param handler an effective handler for the matcher
     * @param methods accepted methods. If empty then all methods are accepted
     */
    HandlerRoute(List<Service> serviceContext, PathMatcher pathMatcher, Handler handler, Http.RequestMethod... methods) {
        this(serviceContext, pathMatcher, handler, Arrays.asList(methods));
    }

    /**
     * Creates new instance accepting ANY path using the {@link #EMPTY_PATH_MATCHER}.
     *
     * @param serviceContext a list of services which defines this route
     * @param handler an effective handler for the matcher.
     * @param methods accepted methods. If empty then all methods are accepted.
     */
    HandlerRoute(List<Service> serviceContext, Handler handler, Http.RequestMethod... methods) {
        this(serviceContext, EMPTY_PATH_MATCHER, handler, methods);
    }

    /**
     * Creates new instance accepting ANY path using the {@link #EMPTY_PATH_MATCHER}.
     *
     * @param serviceContext a list of services which defines this route
     * @param handler an effective handler for the matcher.
     * @param methods accepted methods. If empty then all methods are accepted.
     */
    HandlerRoute(List<Service> serviceContext, Handler handler, Iterable<Http.RequestMethod> methods) {
        this(serviceContext, EMPTY_PATH_MATCHER, handler, methods);
    }

    @Override
    public Set<Http.RequestMethod> acceptedMethods() {
        return methodPredicate.acceptedMethods();
    }

    @Override
    public boolean accepts(Http.RequestMethod method) {
        return methodPredicate.test(method);
    }

    /**
     * Returns an effective {@link Handler}.
     *
     * @return an request / response handler.
     */
    public Handler handler() {
        return handler;
    }

    public Map<String, String> diagnosticEvent() {
        return diagnosticEvent;
    }

    /**
     * Matches this against a URI path. Drops any path parameters before matching.
     *
     * @param path resolved and normalized URI path to test against.
     * @return a {@link PathMatcher.Result} of the test.
     * @throws NullPointerException in case that {@code path} parameter is {@code null}.
     */
    public PathMatcher.Result match(CharSequence path) {
        return pathMatcher.match(extractPathParams(path.toString()));
    }

    @Override
    public String toString() {
        return "HandlerRoute{"
                + "pathMatcher=" + pathMatcher
                + ", handler=" + handler
                + ", methodPredicate=" + methodPredicate
                + '}';
    }
}
