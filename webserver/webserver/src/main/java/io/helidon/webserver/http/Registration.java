/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

/**
 * A routing builder registration.
 * <p>
 * This type is used internally in Helidon to allow gathering of registrations, and then re-applying them on a
 * different builder.
 */
public interface Registration {
    /**
     * Create a registration for service(s) with a path.
     *
     * @param path     path of the service(s)
     * @param services service(s) to register
     * @return a new registration
     */
    static Registration create(String path, HttpService... services) {
        return new Registrations.ServicePathRegistration(path, services);
    }

    /**
     * Create a registration for service(s).
     *
     * @param services service(s) to register
     * @return a new registration
     */
    static Registration create(HttpService... services) {
        return new Registrations.ServiceRegistration(services);
    }

    /**
     * Create a registration for a route.
     *
     * @param route to register
     * @return a new registration
     */
    static Registration create(HttpRoute route) {
        return new Registrations.RouteRegistration(route);
    }

    /**
     * Create a registration for a filter.
     *
     * @param filter to register
     * @return a new registration
     */
    static Registration create(Filter filter) {
        return new Registrations.FilterRegistration(filter);
    }

    /**
     * Create a registration for an error handler.
     *
     * @param exceptionClass class of exception to map this handler to
     * @param handler        handler to handle that exception
     * @param <T> type of the exception to be handled
     * @return a new registration
     */
    static <T> Registration create(Class<T> exceptionClass, ErrorHandler<? super T> handler) {
        return new Registrations.ErrorRegistration<>(exceptionClass, handler);
    }

    /**
     * Create a registration for configuration of max re-route count.
     *
     * @param maxReRouteCount maximal number of re-routes to allow
     * @return a new registration
     */
    static Registration createMaxRerouteCount(int maxReRouteCount) {
        return new Registrations.MaxRerouteCountRegistration(maxReRouteCount);
    }

    /**
     * Create a registration for HTTP security instance.
     *
     * @param security to register
     * @return a new registration
     */
    static Registration create(HttpSecurity security) {
        return new Registrations.SecurityRegistration(security);
    }

    /**
     * Register this registration on a different routing builder.
     *
     * @param routing the routing builder to apply this registration on
     */
    void register(HttpRouting.Builder routing);

}
