package io.helidon.nima.http2.webclient;

import io.helidon.builder.api.Prototype;
import io.helidon.nima.http2.WindowSize;

class Http2ClientConfigSupport {
    static class ProtocolConfigInterceptor implements Prototype.BuilderInterceptor<Http2ClientProtocolConfig.BuilderBase<?, ?>> {
        @Override
        public Http2ClientProtocolConfig.BuilderBase<?, ?> intercept(Http2ClientProtocolConfig.BuilderBase<?, ?> target) {
            int maxFrameSize = target.maxFrameSize();
            if (maxFrameSize < WindowSize.DEFAULT_MAX_FRAME_SIZE || maxFrameSize > WindowSize.MAX_MAX_FRAME_SIZE) {
                throw new IllegalArgumentException(
                        "Max frame size needs to be a number between 2^14(16_384) and 2^24-1(16_777_215)"
                );
            }

            return target;
        }
    }
}
