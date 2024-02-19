package io.helidon.service.inject.api;

import java.util.Set;

import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;

/**
 * Service descriptor to enable injection of String name of a {@link io.helidon.service.inject.api.Injection.DrivenBy}
 * service.
 * <p>
 * Not intended for direct use by users, implementation detail of the service registry, must be public,
 * as it may be used in generated applications
 */
@SuppressWarnings({"checkstyle:TypeName"}) // matches pattern of generated descriptors
public class DrivenByName__ServiceDescriptor implements GeneratedInjectService.Descriptor<String> {
    /**
     * Singleton instance to be referenced when building applications.
     */
    public static final DrivenByName__ServiceDescriptor INSTANCE = new DrivenByName__ServiceDescriptor();

    private static final TypeName INFO_TYPE = TypeName.create(DrivenByName__ServiceDescriptor.class);
    private static final Set<TypeName> CONTRACTS = Set.of(TypeNames.STRING);

    private DrivenByName__ServiceDescriptor() {
    }

    @Override
    public TypeName serviceType() {
        return INFO_TYPE;
    }

    @Override
    public TypeName descriptorType() {
        return INFO_TYPE;
    }

    @Override
    public Set<TypeName> contracts() {
        return CONTRACTS;
    }

    @Override
    public TypeName scope() {
        return Injection.Singleton.TYPE_NAME;
    }
}
