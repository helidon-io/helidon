/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.webserver.observe.metrics;

import io.helidon.builder.api.Prototype;
import io.helidon.common.uri.UriPath;
import io.helidon.config.Config;
import io.helidon.http.Method;
import io.helidon.http.PathMatcher;
import io.helidon.http.PathMatchers;

class AutoHttpMetricsPathConfigSupport {

    private AutoHttpMetricsPathConfigSupport() {
    }

    static class CustomMethods {

        @Prototype.ConfigFactoryMethod
        static PathMatcher createPathMatcher(Config config) {
            return PathMatchers.create(config.as(String.class).get());
        }

        @Prototype.ConfigFactoryMethod
        static Method createMethod(Config config) {
            return Method.create(config.as(String.class).get());
        }

        /**
         * Checks whether the {@link io.helidon.common.uri.UriPath} matches the path in the path config.
         *
         * @param config path config settings
         * @param uriPath {@code UriPath} to test
         * @return true if the {@code UriPath} matches the config's path matcher; false otherwise
         */
        @Prototype.PrototypeMethod
        static boolean matchesPath(AutoHttpMetricsPathConfig config, UriPath uriPath) {
            return config.pathMatcher().match(uriPath).accepted();
        }

        /**
         * Checks whether the {@link io.helidon.http.Method} matches the method(s) in the path config.
         *
         * @param config path config settings
         * @param method {@code Method} to test
         * @return true if the specified method matches the method selection in the config; false otherwise
         */
        @Prototype.PrototypeMethod
        static boolean matchesMethod(AutoHttpMetricsPathConfig config, Method method) {
            return config.methodPredicate().test(method);
        }
    }

    static class BuilderDecorator implements Prototype.BuilderDecorator<AutoHttpMetricsPathConfig.BuilderBase<?, ?>> {

        @Override
        public void decorate(AutoHttpMetricsPathConfig.BuilderBase<?, ?> target) {
            target.methodPredicate(Method.predicate(target.methods().stream().map(Method::create).toList()));
            target.pathMatcher(target.path().map(PathMatchers::create).orElseGet(PathMatchers::any));
        }
    }
}
