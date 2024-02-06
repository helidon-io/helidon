package io.helidon.service.inject.api;

import java.util.Map;

import io.helidon.common.types.TypeName;
import io.helidon.service.registry.ServiceInfo;

public interface InjectRegistrySpi extends InjectRegistry {
    TypeName TYPE = TypeName.create(InjectRegistrySpi.class);

    ScopedRegistry createForScope(TypeName scope, String id, Map<ServiceInfo, Object> initialBindings);
}
