package io.helidon.http;

import java.util.Set;

import io.helidon.common.types.TypeName;
import io.helidon.inject.service.Injection;
import io.helidon.inject.service.InjectionContext;
import io.helidon.inject.service.InterceptionMetadata;
import io.helidon.inject.service.ServiceDescriptor;

/**
 * Service descriptor for types that provide injection providers for headers.
 */
@SuppressWarnings("checkstyle:TypeName") // matches pattern of generated descriptors
@Injection.Descriptor
public class Headers__ServiceDescriptor implements ServiceDescriptor<Headers> {
    /**
     * Singleton instance of this service descriptor.
     */
    public static final ServiceDescriptor<Headers> INSTANCE = new Headers__ServiceDescriptor();
    private static final TypeName HEADERS_TYPE = TypeName.create(Headers.class);
    private static final TypeName DESCRIPTOR_TYPE = TypeName.create(Headers__ServiceDescriptor.class);
    private static final Set<TypeName> CONTRACTS = Set.of(HEADERS_TYPE);

    @Override
    public Object instantiate(InjectionContext ctx, InterceptionMetadata interceptionMetadata) {
        throw new IllegalStateException("Headers service should be specified as initial binding when starting request context");
    }

    @Override
    public TypeName serviceType() {
        return HEADERS_TYPE;
    }

    @Override
    public TypeName infoType() {
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
