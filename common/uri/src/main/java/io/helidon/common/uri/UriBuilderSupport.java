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
            if (target.port() == -1) {
                target.port(defaultPort(target.scheme()));
            }
        }

        private static int defaultPort(String scheme) {
            if ("http".equalsIgnoreCase(scheme)) {
                return 80;
            }
            if ("https".equalsIgnoreCase(scheme)) {
                return 443;
            }
            if (Character.toLowerCase(scheme.charAt(scheme.length() - 1)) == 's') {
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
            int portDelimiter;
            if (authority.startsWith("[")) {
                int closingBracket = authority.lastIndexOf(']');
                portDelimiter = closingBracket >= 0
                        && closingBracket + 1 < authority.length()
                        && authority.charAt(closingBracket + 1) == ':'
                                ? closingBracket + 1
                                : -1;
            } else {
                portDelimiter = authority.lastIndexOf(':');
            }
            String host = portDelimiter < 1 ? authority : authority.substring(0, portDelimiter);
            builder.host(UriEncoding.decodeHost(host));
            if (portDelimiter >= 1) {
                builder.port(Integer.parseInt(authority.substring(portDelimiter + 1)));
            }
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
