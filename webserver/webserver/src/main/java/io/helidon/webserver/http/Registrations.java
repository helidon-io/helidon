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

class Registrations {
    static final class ServicePathRegistration implements Registration {
        private final HttpService[] service;
        private final String path;

        ServicePathRegistration(String path, HttpService... service) {
            this.path = path;
            this.service = service;
        }

        @Override
        public void register(HttpRouting.Builder routing) {
            routing.register(path, service);
        }
    }

    static final class ServiceRegistration implements Registration {
        private final HttpService[] service;

        ServiceRegistration(HttpService... service) {
            this.service = service;
        }

        @Override
        public void register(HttpRouting.Builder routing) {
            routing.register(service);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    static final class ErrorRegistration<T> implements Registration {
        private final Class exceptionClass;
        private final ErrorHandler handler;

        ErrorRegistration(Class<T> exceptionClass, ErrorHandler<? super T> handler) {
            this.exceptionClass = exceptionClass;
            this.handler = handler;
        }

        @Override
        public void register(HttpRouting.Builder routing) {
            routing.error(exceptionClass, handler);
        }
    }

    static final class RouteRegistration implements Registration {
        private final HttpRoute route;

        RouteRegistration(HttpRoute route) {
            this.route = route;
        }

        @Override
        public void register(HttpRouting.Builder routing) {
            routing.route(route);
        }
    }

    static final class FilterRegistration implements Registration {
        private final Filter filter;

        FilterRegistration(Filter filter) {
            this.filter = filter;
        }

        @Override
        public void register(HttpRouting.Builder routing) {
            routing.addFilter(filter);
        }
    }

    static final class MaxRerouteCountRegistration implements Registration {
        private final int maxReRouteCount;

        MaxRerouteCountRegistration(int maxReRouteCount) {
            this.maxReRouteCount = maxReRouteCount;
        }

        @Override
        public void register(HttpRouting.Builder routing) {
            routing.maxReRouteCount(maxReRouteCount);
        }
    }

    static final class SecurityRegistration implements Registration {
        private final HttpSecurity security;

        SecurityRegistration(HttpSecurity security) {
            this.security = security;
        }

        @Override
        public void register(HttpRouting.Builder routing) {
            routing.security(security);
        }
    }
}
