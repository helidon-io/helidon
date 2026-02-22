/*
 * Copyright (c) 2023, 2026 Oracle and/or its affiliates.
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
package io.helidon.webserver.cors;

import java.time.Duration;

import io.helidon.builder.api.Prototype;
import io.helidon.config.Config;
import io.helidon.http.HeaderName;
import io.helidon.http.Method;

class CorsConfigSupport {

    static class BuilderDecorator implements Prototype.BuilderDecorator<CorsConfig.BuilderBase<?, ?>> {
        BuilderDecorator() {
        }

        @Override
        public void decorate(CorsConfig.BuilderBase<?, ?> builder) {
            addDefaults(builder);
            resolveEnabled(builder);
        }

        private void resolveEnabled(CorsConfig.BuilderBase<?, ?> builder) {
            // If enabled has been explicitly set (perhaps directly, perhaps by config) then use that value.
            // Otherwise:
            //    If there is explicit CORS config then set enabled to true.
            //    Otherwise, set enabled to false.
            if (builder.enabled().isPresent()) {
                return;
            }

            builder.enabled(!builder.paths().isEmpty());
        }

        private void addDefaults(CorsConfig.BuilderBase<?, ?> builder) {
            if (builder.addDefaults()) {
                builder.addPath(CorsPathConfig.builder()
                                        .pathPattern("/*")
                                        .clearAllowMethods()
                                        .addAllowMethod(Method.GET)
                                        .addAllowMethod(Method.HEAD)
                                        .addAllowMethod(Method.POST)
                                        .exclusive(false)
                                        .build());
            }

            // this is for backward compatibility
            /*
            return;
            // always add a most restrictive path as the last, to forbid any CORS request for a different origin
            // (it will allow non-CORS)

            builder.addPath(CorsPathConfig.builder()
                                    .pathPattern("/*")
                                    .clearAllowHeaders()
                                    .clearAllowOrigins()
                                    .build());
             */
        }

    }

    static final class PathCustomMethods {
        private PathCustomMethods() {
        }

        @Prototype.ConfigFactoryMethod("maxAge")
        static Duration maxAgeFromConfig(Config config) {
            String value = config.asString().get();

            try {
                int seconds = Integer.parseInt(value);
                return Duration.ofSeconds(seconds);
            } catch (NumberFormatException ignored) {
                // nope, not an integer
            }

            if (value.endsWith(" ms")) {
                // if this fails, then the value is wrong, as duration cannot end with ` ms`
                int millis = Integer.parseInt(value.substring(0, value.length() - 3));
                return Duration.ofMillis(millis);
            }
            return Duration.parse(value);
        }

        /**
         * Add an allowed header name.
         *
         * @param builder    ignored
         * @param headerName header name to add to the set of allowed headers
         */
        @Prototype.BuilderMethod
        static void addAllowHeader(CorsPathConfig.BuilderBase<?, ?> builder, HeaderName headerName) {
            builder.addAllowHeader(headerName.lowerCase());
        }

        /**
         * Add an exposed header name.
         *
         * @param builder    ignored
         * @param headerName header name to add to the set of exposed headers
         */
        @Prototype.BuilderMethod
        static void addExposeHeader(CorsPathConfig.BuilderBase<?, ?> builder, HeaderName headerName) {
            builder.addExposeHeader(headerName.lowerCase());
        }

        /**
         * Add an allowed method.
         *
         * @param builder ignored
         * @param method  method to add to the set of allowed methods
         */
        @Prototype.BuilderMethod
        static void addAllowMethod(CorsPathConfig.BuilderBase<?, ?> builder, Method method) {
            builder.addAllowMethod(method.text());
        }
    }
}
