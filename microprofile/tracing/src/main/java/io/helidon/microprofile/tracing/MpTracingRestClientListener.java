package io.helidon.microprofile.tracing;

import javax.ws.rs.Priorities;

import io.helidon.tracing.jersey.client.ClientTracingFilter;

import org.eclipse.microprofile.opentracing.Traced;
import org.eclipse.microprofile.rest.client.RestClientBuilder;
import org.eclipse.microprofile.rest.client.spi.RestClientListener;

/**
 * Tracing extension for Rest Client.
 * Registers a filter that reads {@link org.eclipse.microprofile.opentracing.Traced} from methods to configure (or reconfigure)
 * tracing.
 */
public class MpTracingRestClientListener implements RestClientListener {
    private static final ClientTracingFilter FILTER = new ClientTracingFilter();
    private static final MpTracingRestClientFilter REST_CLIENT_FILTER = new MpTracingRestClientFilter();

    @Override
    public void onNewClient(Class<?> serviceInterface, RestClientBuilder builder) {
        Traced traced = serviceInterface.getAnnotation(Traced.class);

        boolean enabled;
        String opName;

        if (null != traced) {
            enabled = traced.value();
            opName = traced.operationName();
        } else {
            enabled = true;
            opName = "";
        }

        builder.register(REST_CLIENT_FILTER, Priorities.AUTHENTICATION - 300);
        builder.register(FILTER, Priorities.AUTHENTICATION - 250);
        builder.executorService(MpTracingClientRegistrar.EXECUTOR_SERVICE.get());
        if (!opName.isEmpty()) {
            builder.property(ClientTracingFilter.SPAN_NAME_PROPERTY_NAME, opName);
        }

        builder.property(ClientTracingFilter.ENABLED_PROPERTY_NAME, enabled);
    }
}
