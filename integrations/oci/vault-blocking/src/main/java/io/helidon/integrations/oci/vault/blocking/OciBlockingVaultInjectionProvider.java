package io.helidon.integrations.oci.vault.blocking;

import java.lang.reflect.Type;
import java.util.Set;

import io.helidon.config.Config;
import io.helidon.integrations.oci.connect.OciRestApi;
import io.helidon.integrations.oci.connect.spi.InjectionProvider;

public class OciBlockingVaultInjectionProvider implements InjectionProvider<OciVault> {
    @Override
    public Set<Type> types() {
        return Set.of(OciVault.class);
    }

    @Override
    public OciVault createInstance(OciRestApi restApi, Config ociConfig) {
        return BlockingOciVaultImpl.create(restApi, ociConfig);
    }
}
