/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.webserver.http;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import io.helidon.http.Method;
import io.helidon.http.PathMatcher;
import io.helidon.http.PathMatchers;

/**
 * HTTP Routing rules, used by both {@link HttpRouting.Builder}
 * and by {@link HttpService#routing(HttpRules)}.
 */
public interface HttpRules {
    /**
     * Register a service on the current path.
     *
     * @param service service to register
     * @return updated rules
     */
    HttpRules register(HttpService... service);

    /**
     * Register a service on the current path.
     *
     * @param service service to register
     * @return updated rules
     */
    default HttpRules register(Supplier<? extends HttpService> service) {
        return register(service.get());
    }

    /**
     * Register two services on the current path.
     *
     * @param service1 first service to register
     * @param service2 second service to register
     * @return updated rules
     */
    default HttpRules register(Supplier<? extends HttpService> service1,
                               Supplier<? extends HttpService> service2) {
        return register(service1.get(), service2.get());
    }

    /**
     * Register three services on the current path.
     *
     * @param service1 first service to register
     * @param service2 second service to register
     * @param service3 third service to register
     * @return updated rules
     */
    default HttpRules register(Supplier<? extends HttpService> service1,
                               Supplier<? extends HttpService> service2,
                               Supplier<? extends HttpService> service3) {
        return register(service1.get(), service2.get(), service3.get());
    }

    /**
     * Register four services on the current path.
     *
     * @param service1 first service to register
     * @param service2 second service to register
     * @param service3 third service to register
     * @param service4 fourth service to register
     * @return updated rules
     */
    default HttpRules register(Supplier<? extends HttpService> service1,
                               Supplier<? extends HttpService> service2,
                               Supplier<? extends HttpService> service3,
                               Supplier<? extends HttpService> service4) {
        return register(service1.get(), service2.get(), service3.get(), service4.get());
    }

    /**
     * Register five services on the current path.
     *
     * @param service1 first service to register
     * @param service2 second service to register
     * @param service3 third service to register
     * @param service4 fourth service to register
     * @param service5 fifth service to register
     * @return updated rules
     */
    default HttpRules register(Supplier<? extends HttpService> service1,
                               Supplier<? extends HttpService> service2,
                               Supplier<? extends HttpService> service3,
                               Supplier<? extends HttpService> service4,
                               Supplier<? extends HttpService> service5) {
        return register(service1.get(), service2.get(), service3.get(), service4.get(), service5.get());
    }

    /**
     * Register services on the current path.
     *
     * @param services services to register
     * @return updated rules
     */
    default HttpRules register(List<Supplier<? extends HttpService>> services) {
        return register(services.stream().map(Supplier::get).toArray(HttpService[]::new));
    }

    /**
     * Register a service on sub-path of the current path.
     *
     * @param pathPattern URI path pattern
     * @param service     service to register
     * @return updated rules
     */
    HttpRules register(String pathPattern, HttpService... service);

    /**
     * Register a service on sub-path of the current path.
     *
     * @param pathPattern URI path pattern
     * @param service     service to register
     * @return updated rules
     */
    default HttpRules register(String pathPattern, Supplier<? extends HttpService> service) {
        return register(pathPattern, service.get());
    }

    /**
     * Register two services on sub-path of the current path.
     *
     * @param pathPattern URI path pattern
     * @param service1    first service to register
     * @param service2    second service to register
     * @return updated rules
     */
    default HttpRules register(String pathPattern,
                               Supplier<? extends HttpService> service1,
                               Supplier<? extends HttpService> service2) {
        return register(pathPattern, service1.get(), service2.get());
    }

    /**
     * Register three services on sub-path of the current path.
     *
     * @param pathPattern URI path pattern
     * @param service1    first service to register
     * @param service2    second service to register
     * @param service3    third service to register
     * @return updated rules
     */
    default HttpRules register(String pathPattern,
                               Supplier<? extends HttpService> service1,
                               Supplier<? extends HttpService> service2,
                               Supplier<? extends HttpService> service3) {
        return register(pathPattern, service1.get(), service2.get(), service3.get());
    }

    /**
     * Register four services on sub-path of the current path.
     *
     * @param pathPattern URI path pattern
     * @param service1    first service to register
     * @param service2    second service to register
     * @param service3    third service to register
     * @param service4    fourth service to register
     * @return updated rules
     */
    default HttpRules register(String pathPattern,
                               Supplier<? extends HttpService> service1,
                               Supplier<? extends HttpService> service2,
                               Supplier<? extends HttpService> service3,
                               Supplier<? extends HttpService> service4) {
        return register(pathPattern, service1.get(), service2.get(), service3.get(), service4.get());
    }

    /**
     * Register five services on sub-path of the current path.
     *
     * @param pathPattern URI path pattern
     * @param service1    first service to register
     * @param service2    second service to register
     * @param service3    third service to register
     * @param service4    fourth service to register
     * @param service5    fifth service to register
     * @return updated rules
     */
    default HttpRules register(String pathPattern,
                               Supplier<? extends HttpService> service1,
                               Supplier<? extends HttpService> service2,
                               Supplier<? extends HttpService> service3,
                               Supplier<? extends HttpService> service4,
                               Supplier<? extends HttpService> service5) {
        return register(pathPattern, service1.get(), service2.get(), service3.get(), service4.get(), service5.get());
    }

    /**
     * Register services on sub-path of the current path.
     *
     * @param pathPattern URI path pattern
     * @param services    services to register
     * @return updated rules
     */
    default HttpRules register(String pathPattern, List<Supplier<? extends HttpService>> services) {
        return register(pathPattern, services.stream().map(Supplier::get).toArray(HttpService[]::new));
    }

    /**
     * Add a route. This allows also protocol version specific routing.
     *
     * @param route route to add
     * @return updated rules
     */
    HttpRules route(HttpRoute route);

    /**
     * Add a route. This allows also protocol version specific routing.
     *
     * @param route route to add
     * @return updated rules
     */
    default HttpRules route(Supplier<? extends HttpRoute> route) {
        return route(route.get());
    }

    /**
     * Add a route.
     *
     * @param method      HTTP method to handle
     * @param pathPattern URI path pattern
     * @param handler     handler to process HTTP request
     * @return updated rules
     */
    default HttpRules route(Method method, String pathPattern, Handler handler) {
        return route(Method.predicate(method), PathMatchers.create(pathPattern), handler);
    }

    /**
     * Add a route.
     *
     * @param method      HTTP method to handle
     * @param pathMatcher URI path matcher, see {@link io.helidon.http.PathMatchers#create(String)}
     * @param handler     handler to process HTTP request
     * @return updated rules
     */
    default HttpRules route(Method method, PathMatcher pathMatcher, Handler handler) {
        return route(Method.predicate(method), pathMatcher, handler);
    }

    /**
     * Add a route.
     *
     * @param methodPredicate HTTP method predicate, see {@link io.helidon.http.Method#predicate(io.helidon.http.Method...)}
     * @param pathMatcher     URI path matcher, see {@link io.helidon.http.PathMatchers#create(String)}
     * @param handler         handler to process HTTP request
     * @return updated rules
     */
    default HttpRules route(Predicate<Method> methodPredicate, PathMatcher pathMatcher, Handler handler) {
        return route(HttpRoute.builder()
                             .methods(methodPredicate)
                             .path(pathMatcher)
                             .handler(handler)
                             .build());
    }

    /**
     * Add a route.
     *
     * @param method  HTTP method to handle
     * @param handler handler to process HTTP request
     * @return updated rules
     */
    default HttpRules route(Method method, Handler handler) {
        return route(HttpRoute.builder()
                             .methods(method)
                             .handler(handler)
                             .build());
    }

    /**
     * Add a get route.
     *
     * @param pathPattern URI path pattern
     * @param handlers    handlers to process HTTP request
     * @return updated rules
     */
    default HttpRules get(String pathPattern, Handler... handlers) {
        for (Handler handler : handlers) {
            route(Method.GET, pathPattern, handler);
        }
        return this;
    }

    /**
     * Add a get route.
     *
     * @param handlers handlers to process HTTP request
     * @return updated rules
     */
    default HttpRules get(Handler... handlers) {
        for (Handler handler : handlers) {
            route(Method.GET, handler);
        }
        return this;
    }

    /**
     * Add a post route.
     *
     * @param pathPattern URI path pattern
     * @param handlers    handlers to process HTTP request
     * @return updated rules
     */
    default HttpRules post(String pathPattern, Handler... handlers) {
        for (Handler handler : handlers) {
            route(Method.POST, pathPattern, handler);
        }
        return this;
    }

    /**
     * Add a post route.
     *
     * @param handlers handlers to process HTTP request
     * @return updated rules
     */
    default HttpRules post(Handler... handlers) {
        for (Handler handler : handlers) {
            route(Method.POST, handler);
        }
        return this;
    }

    /**
     * Add a put route.
     *
     * @param pathPattern URI path pattern
     * @param handlers    handlers to process HTTP request
     * @return updated rules
     */
    default HttpRules put(String pathPattern, Handler... handlers) {
        for (Handler handler : handlers) {
            route(Method.PUT, pathPattern, handler);
        }
        return this;
    }

    /**
     * Add a put route.
     *
     * @param handlers handlers to process HTTP request
     * @return updated rules
     */
    default HttpRules put(Handler... handlers) {
        for (Handler handler : handlers) {
            route(Method.PUT, handler);
        }
        return this;
    }

    /**
     * Add a delete route.
     *
     * @param pathPattern URI path pattern
     * @param handlers    handlers to process HTTP request
     * @return updated rules
     */
    default HttpRules delete(String pathPattern, Handler... handlers) {
        for (Handler handler : handlers) {
            route(Method.DELETE, pathPattern, handler);
        }
        return this;
    }

    /**
     * Add a delete route.
     *
     * @param handlers handlers to process HTTP request
     * @return updated rules
     */
    default HttpRules delete(Handler... handlers) {
        for (Handler handler : handlers) {
            route(Method.DELETE, handler);
        }
        return this;
    }

    /**
     * Add a head route.
     *
     * @param pathPattern URI path pattern
     * @param handlers    handlers to process HTTP request
     * @return updated rules
     */
    default HttpRules head(String pathPattern, Handler... handlers) {
        for (Handler handler : handlers) {
            route(Method.HEAD, pathPattern, handler);
        }
        return this;
    }

    /**
     * Add a head route.
     *
     * @param handlers handlers to process HTTP request
     * @return updated rules
     */
    default HttpRules head(Handler... handlers) {
        for (Handler handler : handlers) {
            route(Method.HEAD, handler);
        }
        return this;
    }

    /**
     * Add an options route.
     *
     * @param pathPattern URI path pattern
     * @param handlers    handlers to process HTTP request
     * @return updated rules
     */
    default HttpRules options(String pathPattern, Handler... handlers) {
        for (Handler handler : handlers) {
            route(Method.OPTIONS, pathPattern, handler);
        }
        return this;
    }

    /**
     * Add an options route.
     *
     * @param handlers handlers to process HTTP request
     * @return updated rules
     */
    default HttpRules options(Handler... handlers) {
        for (Handler handler : handlers) {
            route(Method.OPTIONS, handler);
        }
        return this;
    }

    /**
     * Add an options route.
     *
     * @param pathPattern URI path pattern
     * @param handlers    handlers to process HTTP request
     * @return updated rules
     */
    default HttpRules trace(String pathPattern, Handler... handlers) {
        for (Handler handler : handlers) {
            route(Method.TRACE, pathPattern, handler);
        }
        return this;
    }

    /**
     * Add an options route.
     *
     * @param handlers handlers to process HTTP request
     * @return updated rules
     */
    default HttpRules trace(Handler... handlers) {
        for (Handler handler : handlers) {
            route(Method.TRACE, handler);
        }
        return this;
    }

    /**
     * Add an options route.
     *
     * @param pathPattern URI path pattern
     * @param handlers    handlers to process HTTP request
     * @return updated rules
     */
    default HttpRules patch(String pathPattern, Handler... handlers) {
        for (Handler handler : handlers) {
            route(Method.PATCH, pathPattern, handler);
        }
        return this;
    }

    /**
     * Add an options route.
     *
     * @param handlers handlers to process HTTP request
     * @return updated rules
     */
    default HttpRules patch(Handler... handlers) {
        for (Handler handler : handlers) {
            route(Method.PATCH, handler);
        }
        return this;
    }

    /**
     * Add a route that executes on any HTTP method and any path.
     *
     * @param pathPattern URI path pattern
     * @param handlers    handlers to process HTTP request
     * @return updated rules
     */
    default HttpRules any(String pathPattern, Handler... handlers) {
        for (Handler handler : handlers) {
            route(HttpRoute.builder()
                          .path(pathPattern)
                          .handler(handler));
        }
        return this;
    }

    /**
     * Add a route that executes on any HTTP method and any path.
     *
     * @param handlers handlers to process HTTP request
     * @return updated rules
     */
    default HttpRules any(Handler... handlers) {
        for (Handler handler : handlers) {
            route(HttpRoute.builder()
                          .handler(handler));
        }
        return this;
    }

    /**
     * Add a route.
     *
     * @param method      HTTP method to handle
     * @param pathPattern URI path pattern
     * @param handler     handler as a consumer of {@link ServerRequest}
     * @return updated builder
     */
    default HttpRules route(Method method, String pathPattern, Consumer<ServerRequest> handler) {
        return route(HttpRoute.builder()
                             .methods(method)
                             .path(pathPattern)
                             .handler(Handler.create(handler)));
    }

    /**
     * Add a route.
     *
     * @param method      HTTP method to handle
     * @param pathPattern URI path pattern
     * @param handler     handler as a function that gets {@link ServerRequest} and returns an entity
     * @return updated builder
     */
    default HttpRules route(Method method, String pathPattern, Function<ServerRequest, ?> handler) {
        return route(HttpRoute.builder()
                             .methods(method)
                             .path(pathPattern)
                             .handler(Handler.create(handler)));
    }

    /**
     * Add a route.
     *
     * @param method      HTTP method to handle
     * @param pathPattern URI path pattern
     * @param handler     supplier of entity
     * @return updated builder
     */
    default HttpRules route(Method method, String pathPattern, Supplier<?> handler) {
        return route(HttpRoute.builder()
                             .methods(method)
                             .path(pathPattern)
                             .handler(Handler.create(handler)));
    }

}
