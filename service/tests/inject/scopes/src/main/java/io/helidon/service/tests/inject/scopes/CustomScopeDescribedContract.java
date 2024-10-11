package io.helidon.service.tests.inject.scopes;

import io.helidon.service.inject.api.Injection;
import io.helidon.service.registry.Service;

@Injection.Describe(CustomScope.class)
@Service.Contract
interface CustomScopeDescribedContract {
    String message();
}
