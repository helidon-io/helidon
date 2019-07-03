package io.helidon.microprofile.tracing;

import javax.enterprise.context.RequestScoped;
import javax.enterprise.inject.Produces;

import io.helidon.common.context.Contexts;

import io.opentracing.Tracer;
import io.opentracing.util.GlobalTracer;

/**
 * A producer of {@link io.opentracing.Tracer} needed for injection into {@code CDI} beans.
 */
@RequestScoped
public class TracerProducer {

    /**
     * Provides an instance of tracer currently configured.
     * @return a {@link Tracer} from current {@link io.helidon.common.context.Context},
     *  or {@link io.opentracing.util.GlobalTracer#get()} in case we are not within a context.
     */
    @Produces
    public Tracer tracer() {
        return Contexts.context()
                .flatMap(ctx -> ctx.get(Tracer.class))
                .orElseGet(GlobalTracer::get);
    }
}
