package io.helidon.grpc.server;


import io.grpc.BindableService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


/**
 * @author Aleksandar Seovic  2019.02.12
 */
public interface GrpcRouting
    {
    Iterable<BindableService> services();

    static Builder builder()
        {
        return new Builder();
        }

    final class Builder implements io.helidon.common.Builder<GrpcRouting>
        {
        private List<BindableService> bindableServices = new ArrayList<>();

        public Builder register(GrpcService... services)
            {
            for (GrpcService service : services)
                {
                bindableServices.add(GrpcService.builder(service).build());
                }
            return this;
            }

        public Builder register(BindableService... services)
            {
            bindableServices.addAll(Arrays.asList(services));
            return this;
            }

        public GrpcRouting build()
            {
            return new GrpcRoutingImpl(bindableServices);
            }
        }
    }
