package io.helidon.inject;

import java.util.List;
import java.util.Map;

import io.helidon.inject.service.Injection;

class ServiceScopeServices extends ScopeServices {
    ServiceScopeServices(Services serviceRegistry, String id) {
        super(serviceRegistry, Injection.Service.TYPE_NAME, id, List.of(), Map.of());
    }
}
