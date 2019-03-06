package io.helidon.grpc.server;


import io.grpc.BindableService;
import io.grpc.ServerInterceptor;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.Collection;
import java.util.function.Consumer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.eclipse.microprofile.health.HealthCheck;


/**
 * @author Aleksandar Seovic  2019.02.12
 */
public interface GrpcRouting
    {
    List<GrpcService.ServiceConfig> services();

    List<ServerInterceptor> interceptors();

    static Builder builder()
        {
        return new Builder();
        }

    final class Builder implements io.helidon.common.Builder<GrpcRouting>
        {
        private List<GrpcService.ServiceConfig> services = new ArrayList<>();

        private List<ServerInterceptor> interceptors = new ArrayList<>();

        public Builder intercept(ServerInterceptor... interceptors)
            {
            Collections.addAll(this.interceptors, Objects.requireNonNull(interceptors));
            return this;
            }

        public Builder register(GrpcService service)
            {
            service = GrpcService.builder(service).build();
            return registerInternal(service, service.hc(), null);
            }

        public Builder register(GrpcService service, Consumer<GrpcService.ServiceConfig> configurer)
            {
            service = GrpcService.builder(service).build();
            return registerInternal(service, service.hc(), configurer);
            }

        public Builder register(BindableService service, Consumer<GrpcService.ServiceConfig> configurer)
            {
            GrpcService.ServiceConfig config = new GrpcService.ServiceConfig(service);
            if (configurer != null)
                {
                configurer.accept(config);
                }
            this.services.add(config);
            return this;
            }

        private Builder registerInternal(BindableService service, HealthCheck healthCheck, Consumer<GrpcService.ServiceConfig> configurer)
            {
            GrpcService.ServiceConfig config = new GrpcService.ServiceConfig(service);
            if (healthCheck != null)
                {
                config.healthCheck(healthCheck);
                }
            if (configurer != null)
                {
                configurer.accept(config);
                }
            this.services.add(config);
            return this;
            }

        public GrpcRouting build()
            {
            return new GrpcRoutingImpl(services, interceptors);
            }
        }
    }
