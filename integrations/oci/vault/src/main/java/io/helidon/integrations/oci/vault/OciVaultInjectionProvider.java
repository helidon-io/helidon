package io.helidon.integrations.oci.vault;

import java.util.LinkedList;
import java.util.List;

import io.helidon.integrations.oci.connect.spi.InjectionProvider;

public class OciVaultInjectionProvider implements InjectionProvider {
    private static final List<InjectionType<?>> INJECTABLES;

    static {
        List<InjectionType<?>> injectables = new LinkedList<>();

        injectables.add(InjectionType.create(OciVaultRx.class,
                                             (restApi, config) -> OciVaultRx.builder()
                                                     .restApi(restApi)
                                                     .config(config)
                                                     .build()));

        injectables.add(InjectionType.create(OciVault.class,
                                             (restApi, config) -> OciVault.create(OciVaultRx.builder()
                                                                                          .restApi(restApi)
                                                                                          .config(config)
                                                                                          .build())));

        INJECTABLES = List.copyOf(injectables);
    }

    @Override
    public List<InjectionType<?>> injectables() {
        return INJECTABLES;
    }
}
