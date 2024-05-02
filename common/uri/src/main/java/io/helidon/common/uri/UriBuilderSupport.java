/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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

package io.helidon.common.uri;

import io.helidon.builder.api.Prototype;

final class UriBuilderSupport {
    private UriBuilderSupport() {
    }

    static final class UriInfoInterceptor implements Prototype.BuilderDecorator<UriInfo.BuilderBase<?, ?>> {
        UriInfoInterceptor() {
        }

        @Override
        public void decorate(UriInfo.BuilderBase<?, ?> target) {
            if (target.port() == 0) {
                target.port(defaultPort(target.scheme()));
            }
        }

        private static int defaultPort(String scheme) {
            if ("http".equals(scheme)) {
                return 80;
            }
            if ("https".equals(scheme)) {
                return 443;
            }
            if (scheme.charAt(scheme.length() - 1) == 's') {
                return 443;
            }
            return 80;
        }
    }

    static final class UriInfoCustomMethods {
        private UriInfoCustomMethods() {
        }

        /**
         * Authority of the request, to be converted to host and port.
         *
         * @param authority authority of the request (host:port)
         */
        @Prototype.BuilderMethod
        static void authority(UriInfo.BuilderBase<?, ?> builder, String authority) {
            int index = authority.lastIndexOf(':');
            if (index < 1) {
                // no colon, no port
                builder.host(authority);
                return;
            }
            // this may still be an IPv6 address
            if (authority.charAt(authority.length() - 1) == ']') {
                // IPv6 without port
                builder.host(authority);
                return;
            }
            builder.host(authority.substring(0, index));
            builder.port(Integer.parseInt(authority.substring(index + 1)));
        }

        /**
         * Path of the request, to be converted to {@link io.helidon.common.uri.UriPath}.
         *
         * @param path of the request
         */
        @Prototype.BuilderMethod
        static void path(UriInfo.BuilderBase<?, ?> builder, String path) {
            builder.path(UriPath.createFromDecoded(path));
        }
    }
}
