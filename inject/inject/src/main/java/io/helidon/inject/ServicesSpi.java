package io.helidon.inject;

import java.util.Map;

import io.helidon.common.types.TypeName;
import io.helidon.inject.service.ServiceDescriptor;

public interface ServicesSpi {
    TypeName TYPE_NAME = TypeName.create(ServicesSpi.class);
    ScopeServices createForScope(TypeName scope, String id, Map<ServiceDescriptor<?>, Object> initialBindings);
}
