package io.helidon.builder.test.testsubjects;

import io.helidon.common.config.ConfiguredProvider;
import io.helidon.common.config.NamedService;

public interface ProviderNoImpls extends ConfiguredProvider<ProviderNoImpls.SomeService> {
    interface SomeService extends NamedService {
        String prop();
    }
}
