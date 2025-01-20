package io.helidon.integrations.langchain4j;

import io.helidon.service.registry.Lookup;
import io.helidon.service.registry.Qualifier;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.ServiceRegistry;

public class RegistryHelper {
    private RegistryHelper() {
    }

    public static <T> T named(ServiceRegistry registry, Class<T> clazz, String name) {
        if (Service.Named.DEFAULT_NAME.equals(name)) {
            return registry.get(clazz);
        } else {
            return registry.get(Lookup.builder()
                                        .addContract(clazz)
                                        .addQualifier(Qualifier.createNamed(name))
                                        .build());
        }
    }
}
