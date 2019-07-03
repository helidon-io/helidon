package io.helidon.microprofile.tracing;

import javax.enterprise.event.Observes;
import javax.enterprise.inject.spi.BeforeBeanDiscovery;
import javax.enterprise.inject.spi.Extension;

/**
 * CDI extension for Microprofile Tracing implementation.
 */
public class MpTracingCdiExtension implements Extension {
    /**
     * Add our beans to CDI, so we do not need to use {@code beans.xml}.
     *
     * @param bbd CDI event
     */
    public void observeBeforeBeanDiscovery(@Observes BeforeBeanDiscovery bbd) {
        bbd.addAnnotatedType(MpTracingInterceptor.class, "TracingInterceptor");
        bbd.addAnnotatedType(TracerProducer.class, "TracingTracerProducer");
    }

}
