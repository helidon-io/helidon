package io.helidon.webserver;

import java.util.Set;

import io.helidon.common.types.TypeName;
import io.helidon.service.inject.api.GeneratedInjectService;
import io.helidon.service.inject.api.GeneratedInjectService.Descriptor;
import io.helidon.service.inject.api.Injection;
import io.helidon.service.registry.DependencyContext;
import io.helidon.service.registry.Service;
import io.helidon.webserver.http.ServerRequest;

/**
 * Service descriptor for types that provide injection providers for {@link io.helidon.webserver.http.ServerRequest}.
 */
@SuppressWarnings("checkstyle:TypeName") // matches pattern of generated descriptors
@Service.Descriptor(registryType = "inject",
                    contracts = ServerRequest.class)
public class ServerRequest__ServiceDescriptor implements Descriptor<ServerRequest> {
    /**
     * Singleton instance of this service descriptor.
     */
    public static final Descriptor<ServerRequest> INSTANCE = new ServerRequest__ServiceDescriptor();
    private static final TypeName CONTRACT = TypeName.create(ServerRequest.class);
    private static final TypeName DESCRIPTOR_TYPE = TypeName.create(ServerRequest__ServiceDescriptor.class);
    private static final Set<TypeName> CONTRACTS = Set.of(CONTRACT);

    @Override
    public Object instantiate(DependencyContext ctx, GeneratedInjectService.InterceptionMetadata interceptionMetadata) {
        throw new IllegalStateException("ServerRequest should be specified as initial binding when starting request context,"
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
