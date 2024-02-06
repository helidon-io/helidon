package io.helidon.http;

import java.util.Set;

import io.helidon.common.types.TypeName;
import io.helidon.service.inject.api.GeneratedInjectService.Descriptor;
import io.helidon.service.inject.api.GeneratedInjectService.InterceptionMetadata;
import io.helidon.service.inject.api.Injection;
import io.helidon.service.registry.DependencyContext;
import io.helidon.service.registry.Service;

/**
 * Service descriptor for types that provide injection providers for headers.
 */
@SuppressWarnings("checkstyle:TypeName") // matches pattern of generated descriptors
@Service.Descriptor(
        registryType = "inject",
        contracts = Headers.class)
public class Headers__ServiceDescriptor implements Descriptor<Headers> {
    /**
     * Singleton instance of this service descriptor.
     */
    public static final Descriptor<Headers> INSTANCE = new Headers__ServiceDescriptor();
    private static final TypeName HEADERS_TYPE = TypeName.create(Headers.class);
    private static final TypeName DESCRIPTOR_TYPE = TypeName.create(Headers__ServiceDescriptor.class);
    private static final Set<TypeName> CONTRACTS = Set.of(HEADERS_TYPE);

    @Override
    public Object instantiate(DependencyContext ctx, InterceptionMetadata interceptionMetadata) {
        throw new IllegalStateException("Headers service should be specified as initial binding when starting request context");
    }

    @Override
    public TypeName serviceType() {
        return HEADERS_TYPE;
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
