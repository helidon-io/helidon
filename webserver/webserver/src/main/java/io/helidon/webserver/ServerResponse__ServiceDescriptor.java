package io.helidon.webserver;

import java.util.Set;

import io.helidon.common.types.TypeName;
import io.helidon.service.inject.api.GeneratedInjectService;
import io.helidon.service.inject.api.GeneratedInjectService.Descriptor;
import io.helidon.service.inject.api.Injection;
import io.helidon.service.registry.DependencyContext;
import io.helidon.service.registry.Service;
import io.helidon.webserver.http.ServerResponse;

/**
 * Service descriptor for types that provide injection providers for {@link io.helidon.webserver.http.ServerResponse}.
 */
@SuppressWarnings("checkstyle:TypeName") // matches pattern of generated descriptors
@Service.Descriptor(registryType = "inject",
                    contracts = ServerResponse.class)
public class ServerResponse__ServiceDescriptor implements Descriptor<ServerResponse> {
    /**
     * Singleton instance of this service descriptor.
     */
    public static final Descriptor<ServerResponse> INSTANCE = new ServerResponse__ServiceDescriptor();
    private static final TypeName CONTRACT = TypeName.create(ServerResponse.class);
    private static final TypeName DESCRIPTOR_TYPE = TypeName.create(ServerResponse__ServiceDescriptor.class);
    private static final Set<TypeName> CONTRACTS = Set.of(CONTRACT);

    @Override
    public Object instantiate(DependencyContext ctx, GeneratedInjectService.InterceptionMetadata interceptionMetadata) {
        throw new IllegalStateException("ServerResponse should be specified as initial binding when starting request context,"
                                                + " if used in any service.");
    }

    @Override
    public TypeName serviceType() {
        return CONTRACT;
    }

    @Override
    public TypeName descriptorType() {
        return DESCRIPTOR_TYPE;
    }

    @Override
    public Set<TypeName> contracts() {
        return CONTRACTS;
    }

    @Override
    public TypeName scope() {
        return Injection.RequestScope.TYPE_NAME;
    }
}
