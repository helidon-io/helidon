package io.helidon.nima.webclient.http1;

import io.helidon.builder.api.Prototype;

class Http1ClientConfigSupport {
    private Http1ClientConfigSupport() {
    }

    static class Http1BuilderInterceptor implements Prototype.BuilderInterceptor<Http1ClientConfig.BuilderBase<?, ?>> {
        @Override
        public Http1ClientConfig.BuilderBase<?, ?> intercept(Http1ClientConfig.BuilderBase<?, ?> target) {
            if (target.protocolConfig() == null) {
                target.protocolConfig(Http1ClientProtocolConfig.create());
            }
            return target;
        }
    }
}
