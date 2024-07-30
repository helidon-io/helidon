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

package io.helidon.webserver;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import io.helidon.common.Weighted;
import io.helidon.webserver.http.ErrorHandler;
import io.helidon.webserver.http.Filter;
import io.helidon.webserver.http.HttpFeature;
import io.helidon.webserver.http.HttpRoute;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http.HttpSecurity;
import io.helidon.webserver.http.HttpService;
import io.helidon.webserver.http.Registration;

class ServerToHttpFeatureBuilder implements HttpRouting.Builder {
    private final List<Registration> registrations = new ArrayList<>();

    private final double weight;
    private final HttpRouting.Builder delegate;

    ServerToHttpFeatureBuilder(double weight, HttpRouting.Builder delegate) {
        this.weight = weight;
        this.delegate = delegate;
    }

    @Override
    public HttpRouting.Builder register(HttpService... services) {
        registrations.add(Registration.create(services));
        return this;
    }

    @Override
    public HttpRouting.Builder register(String path, HttpService... services) {
        registrations.add(Registration.create(path, services));
        return this;
    }

    @Override
    public HttpRouting.Builder route(HttpRoute route) {
        registrations.add(Registration.create(route));
        return this;
    }

    @Override
    public HttpRouting.Builder addFilter(Filter filter) {
        registrations.add(Registration.create(filter));
        return this;
    }

    @Override
    public HttpRouting.Builder addFeature(Supplier<? extends HttpFeature> feature) {
        // features are always directly sent to delegate, as they have correct ordering based on weight
        delegate.addFeature(feature);
        return this;
    }

    @Override
    public <T extends Throwable> HttpRouting.Builder error(Class<T> exceptionClass, ErrorHandler<? super T> handler) {
        registrations.add(Registration.create(exceptionClass, handler));
        return this;
    }

    @Override
    public HttpRouting.Builder maxReRouteCount(int maxReRouteCount) {
        registrations.add(Registration.createMaxRerouteCount(maxReRouteCount));
        return this;
    }

    @Override
    public HttpRouting.Builder security(HttpSecurity security) {
        registrations.add(Registration.create(security));
        return this;
    }

    @Override
    public HttpRouting.Builder copy() {
        ServerToHttpFeatureBuilder copy = new ServerToHttpFeatureBuilder(weight, delegate.copy());
        copy.registrations.addAll(this.registrations);
        return copy;
    }

    @Override
    public HttpRouting build() {
        throw new UnsupportedOperationException("This method should never escape internal Helidon types");
    }

    HttpFeature toFeature() {
        return new HttpFeatureForServerFeature(this, weight);
    }

    private static class HttpFeatureForServerFeature implements HttpFeature, Weighted {
        private final ServerToHttpFeatureBuilder builder;
        private final double weight;

        private HttpFeatureForServerFeature(ServerToHttpFeatureBuilder builder, double weight) {
            this.builder = builder;
            this.weight = weight;
        }

        @Override
        public void setup(HttpRouting.Builder routing) {
            builder.registrations.forEach(it -> it.register(routing));
        }

        @Override
        public double weight() {
            return weight;
        }
    }
}
