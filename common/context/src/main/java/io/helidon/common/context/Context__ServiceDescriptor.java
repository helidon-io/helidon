package io.helidon.common.context;

import java.util.Set;

import io.helidon.common.types.TypeName;
import io.helidon.service.inject.api.GeneratedInjectService;
import io.helidon.service.inject.api.GeneratedInjectService.Descriptor;
import io.helidon.service.inject.api.GeneratedInjectService.InterceptionMetadata;
import io.helidon.service.inject.api.Injection;
import io.helidon.service.registry.DependencyContext;
import io.helidon.service.registry.Service;

/**
 * Service descriptor for types that provide injection providers for {@link io.helidon.common.context.Context}.
 */
@SuppressWarnings("checkstyle:TypeName") // matches pattern of generated descriptors
@Service.Descriptor(
        registryType = "inject",
        contracts = Context.class)
public class Context__ServiceDescriptor implements Descriptor<Context> {
    /**
     * Singleton instance of this service descriptor.
     */
    public static final Descriptor<Context> INSTANCE = new Context__ServiceDescriptor();
    private static final TypeName CONTRACT = TypeName.create(Context.class);
    private static final TypeName DESCRIPTOR_TYPE = TypeName.create(Context__ServiceDescriptor.class);
    private static final Set<TypeName> CONTRACTS = Set.of(CONTRACT);

    @Override
    public Object instantiate(DependencyContext ctx, InterceptionMetadata interceptionMetadata) {
        throw new IllegalStateException("Context should be specified as initial binding when starting request context,"
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
