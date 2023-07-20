package io.helidon.common.uri;

import io.helidon.builder.api.Prototype;

final class UriBuilderSupport {
    private UriBuilderSupport() {
    }

    static final class UriInfoInterceptor implements Prototype.BuilderInterceptor<UriInfo.BuilderBase<?, ?>> {
        UriInfoInterceptor() {
        }

        @Override
        public UriInfo.BuilderBase<?, ?> intercept(UriInfo.BuilderBase<?, ?> target) {
            if (target.port() == 0) {
                target.port(defaultPort(target.scheme()));
            }
            return target;
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

        @Prototype.BuilderMethod
        static void path(UriInfo.BuilderBase<?, ?> builder, String path) {
            builder.path(UriPath.createFromDecoded(path));
        }
    }
}
