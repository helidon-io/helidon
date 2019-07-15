package io.helidon.microprofile.opentracing.tck;

import io.helidon.tracing.TracerBuilder;
import io.helidon.tracing.spi.TracerProvider;

/**
 * Created by David Kral.
 */
public class OpentracingJavaMockTracerProvider implements TracerProvider {
    @Override
    public TracerBuilder<?> createBuilder() {
        return OpentracingJavaMockTracerBuilder.create();
    }
}
