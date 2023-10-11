package io.helidon.webserver.observe.tracing;

import io.helidon.builder.api.Prototype;
import io.helidon.tracing.Tracer;
import io.helidon.tracing.config.TracingConfig;

class TracingObserverSupport {
    private TracingObserverSupport() {
    }

    static class TracingObserverDecorator implements Prototype.BuilderDecorator<TracingObserverConfig.BuilderBase<?, ?>> {
        @Override
        public void decorate(TracingObserverConfig.BuilderBase<?, ?> target) {
            TracingConfig env = target.envConfig();
            if (target.enabled()) {
                target.enabled(env.enabled());
            }
            if (target.tracer().isEmpty()) {
                target.tracer(Tracer.global());
            }
        }
    }
}
