package io.helidon.http;

import java.util.Set;

import io.helidon.common.types.TypeName;
import io.helidon.inject.service.Injection;
import io.helidon.inject.service.InjectionContext;
import io.helidon.inject.service.InterceptionMetadata;
import io.helidon.inject.service.ServiceDescriptor;

/**
 * Service descriptor for types that provide injection providers for {@link io.helidon.http.HttpPrologue}.
 */
@SuppressWarnings("checkstyle:TypeName") // matches pattern of generated descriptors
@Injection.Descriptor
public class Prologue__ServiceDescriptor implements ServiceDescriptor<HttpPrologue> {
    /**
     * Singleton instance of this service descriptor.
     */
    public static final ServiceDescriptor<HttpPrologue> INSTANCE = new Prologue__ServiceDescriptor();
    private static final TypeName PROLOGUE_TYPE = TypeName.create(HttpPrologue.class);
    private static final TypeName DESCRIPTOR_TYPE = TypeName.create(Prologue__ServiceDescriptor.class);
    private static final Set<TypeName> CONTRACTS = Set.of(PROLOGUE_TYPE);

    @Override
    public Object instantiate(InjectionContext ctx, InterceptionMetadata interceptionMetadata) {
        throw new IllegalStateException("HttpPrologue should be specified as initial binding when starting request context");
    }

    @Override
    public TypeName serviceType() {
        return PROLOGUE_TYPE;
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
