package io.helidon.common.context;

import java.util.Set;

import io.helidon.common.types.TypeName;
import io.helidon.inject.service.Injection;
import io.helidon.inject.service.InjectionContext;
import io.helidon.inject.service.InterceptionMetadata;
import io.helidon.inject.service.ServiceDescriptor;

/**
 * Service descriptor for types that provide injection providers for {@link io.helidon.common.context.Context}.
 */
@SuppressWarnings("checkstyle:TypeName") // matches pattern of generated descriptors
@Injection.Descriptor
public class Context__ServiceDescriptor implements ServiceDescriptor<Context> {
    /**
     * Singleton instance of this service descriptor.
     */
    public static final ServiceDescriptor<Context> INSTANCE = new Context__ServiceDescriptor();
    private static final TypeName CONTRACT = TypeName.create(Context.class);
    private static final TypeName DESCRIPTOR_TYPE = TypeName.create(Context__ServiceDescriptor.class);
    private static final Set<TypeName> CONTRACTS = Set.of(CONTRACT);

    @Override
    public Object instantiate(InjectionContext ctx, InterceptionMetadata interceptionMetadata) {
        throw new IllegalStateException("Context should be specified as initial binding when starting request context,"
                                                + " if used in any service.");
    }

    @Override
    public TypeName serviceType() {
        return CONTRACT;
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
