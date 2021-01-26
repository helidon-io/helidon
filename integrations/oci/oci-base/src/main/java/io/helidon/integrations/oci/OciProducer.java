package io.helidon.integrations.oci;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import javax.inject.Inject;

import io.helidon.config.ConfigValue;

import com.oracle.bmc.ClientConfiguration;
import com.oracle.bmc.auth.AuthenticationDetailsProvider;

/**
 * Class OciProducer.
 */
@ApplicationScoped
public class OciProducer {
    private static final String OCI_NAME_PREFIX = "oci";

    private AuthenticationDetailsProvider provider;
    private ClientConfiguration clientConfig;

    /**
     * Creates and sets up the {@link AuthenticationDetailsProvider} and {@link ClientConfiguration}.
     *
     * @param config injected from the container.
     */
    @Inject
    public OciProducer(io.helidon.config.Config config) {
        ConfigValue<Oci> configValue = config.get(OCI_NAME_PREFIX).as(Oci::create);
        if (configValue.isPresent()) {
            provider = configValue.get().getProvider();
            clientConfig = configValue.get().getClientConfig();
        } else {
            throw new OciException("OCI cannot be properly configured!");
        }
    }

    /**
     * Produces {@link AuthenticationDetailsProvider}.
     *
     * @return provider.
     */
    @Produces
    public AuthenticationDetailsProvider getProvider() {
        return provider;
    }

    /**
     * Produces {@link ClientConfiguration}.
     *
     * @return clientConfig.
     */
    @Produces
    public ClientConfiguration getClientConfig() {
        return clientConfig;
    }
}
