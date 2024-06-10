/*
 * Copyright (c) 2022, 2024 Oracle and/or its affiliates.
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

import java.util.ArrayList;
import java.util.List;

import io.helidon.common.Weighted;

/**
 * This feature collects business routes (anything that is not a feature or routing configuration) and exposes it as a
 * feature with default weight.
 */
class HttpRoutingFeature implements HttpFeature, Weighted {
    private final List<Registration> registrations = new ArrayList<>();

    HttpRoutingFeature() {
    }

    @Override
    public void setup(HttpRouting.Builder routing) {
        registrations.forEach(it -> it.register(routing));
    }

    void filter(Filter filter) {
        this.registrations.add(Registration.create(filter));
    }

    <T extends Throwable> void error(Class<T> exceptionClass, ErrorHandler<? super T> handler) {
        this.registrations.add(Registration.create(exceptionClass, handler));
    }

    void service(HttpService... services) {
        this.registrations.add(Registration.create(services));
    }

    void service(String path, HttpService... services) {
        this.registrations.add(Registration.create(path, services));
    }

    void route(HttpRoute route) {
        this.registrations.add(Registration.create(route));
    }

    void copyFrom(HttpRoutingFeature mainRouting) {
        this.registrations.addAll(mainRouting.registrations);
    }


}
