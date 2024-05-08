package io.helidon.integrations.oci;

import java.lang.System.Logger.Level;
import java.util.Optional;

import io.helidon.common.LazyValue;
import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.integrations.oci.spi.OciAtnStrategy;
import io.helidon.service.registry.Service;

import com.oracle.bmc.auth.AbstractAuthenticationDetailsProvider;
import com.oracle.bmc.auth.ResourcePrincipalAuthenticationDetailsProvider;

/**
 * Config file based authentication strategy, uses the {@link com.oracle.bmc.auth.ConfigFileAuthenticationDetailsProvider}.
 */
@Weight(Weighted.DEFAULT_WEIGHT - 30)
@Service.Provider
class AtnStrategyResourcePrincipal implements OciAtnStrategy {
    static final String RESOURCE_PRINCIPAL_VERSION_ENV_VAR = "OCI_RESOURCE_PRINCIPAL_VERSION";
    static final String STRATEGY = "resource-principal";

    private static final System.Logger LOGGER = System.getLogger(AtnStrategyResourcePrincipal.class.getName());

    private final LazyValue<Optional<AbstractAuthenticationDetailsProvider>> provider;

    AtnStrategyResourcePrincipal(OciConfig config) {
        provider = createProvider(config);
    }

    @Override
    public String strategy() {
        return STRATEGY;
    }

    @Override
    public Optional<AbstractAuthenticationDetailsProvider> provider() {
        return provider.get();
    }

    private static LazyValue<Optional<AbstractAuthenticationDetailsProvider>> createProvider(OciConfig config) {
        return LazyValue.create(() -> {
            // https://github.com/oracle/oci-java-sdk/blob/v2.19.0/bmc-common/src/main/java/com/oracle/bmc/auth/ResourcePrincipalAuthenticationDetailsProvider.java#L246-L251
            if (System.getenv(RESOURCE_PRINCIPAL_VERSION_ENV_VAR) == null) {
                if (LOGGER.isLoggable(Level.TRACE)) {
                    LOGGER.log(Level.TRACE, "Environment variable \"" + RESOURCE_PRINCIPAL_VERSION_ENV_VAR
                            + "\" is not set, resource principal cannot be used.");
                }
                return Optional.empty();
            }
            return Optional.of(ResourcePrincipalAuthenticationDetailsProvider.builder().build());
        });
    }
}
