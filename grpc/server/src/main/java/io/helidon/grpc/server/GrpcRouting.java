package io.helidon.grpc.server;


import io.grpc.BindableService;
import io.grpc.ServerInterceptor;
import java.util.function.Consumer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;


/**
 * @author Aleksandar Seovic  2019.02.12
 */
public interface GrpcRouting
    {
    Iterable<GrpcService.ServiceConfig> services();

    Iterable<ServerInterceptor> interceptors();

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
            return register(service, null);
            }

        public Builder register(GrpcService service, Consumer<GrpcService.ServiceConfig> configurer)
            {
            BindableService bindableService = GrpcService.builder(service).build();
            return register(bindableService, configurer);
            }

        public Builder register(BindableService service)
            {
            return register(service, null);
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

        public GrpcRouting build()
            {
            return new GrpcRoutingImpl(services, interceptors);
            }
        }
    }
