package io.helidon.integrations.oci;

import java.io.IOException;
import java.lang.System.Logger.Level;
import java.net.InetAddress;
import java.time.Duration;
import java.util.Optional;

import io.helidon.common.LazyValue;
import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.integrations.oci.spi.OciAtnStrategy;
import io.helidon.service.registry.Service;

import com.oracle.bmc.Region;
import com.oracle.bmc.auth.AbstractAuthenticationDetailsProvider;
import com.oracle.bmc.auth.InstancePrincipalsAuthenticationDetailsProvider;

/**
 * Config file based authentication strategy, uses the {@link com.oracle.bmc.auth.ConfigFileAuthenticationDetailsProvider}.
 */
@Weight(Weighted.DEFAULT_WEIGHT - 40)
@Service.Provider
class AtnStrategyInstancePrincipal implements OciAtnStrategy {
    static final String STRATEGY = "instance-principal";

    private static final System.Logger LOGGER = System.getLogger(AtnStrategyInstancePrincipal.class.getName());

    private final LazyValue<Optional<AbstractAuthenticationDetailsProvider>> provider;

    AtnStrategyInstancePrincipal(OciConfig config) {
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
            if (imdsVailable(config)) {
                return Optional.of(InstancePrincipalsAuthenticationDetailsProvider.builder().build());
            }
            return Optional.empty();
        });
    }

    private static boolean imdsVailable(OciConfig config) {
        String imdsAddress = config.imdsAddress();
        Duration timeout = config.imdsTimeout();

        try {
            if (InetAddress.getByName(imdsAddress)
                    .isReachable((int) timeout.toMillis())) {
                return Region.getRegionFromImds("http://" + imdsAddress + "/opc/v2") != null;
            }
            return false;
        } catch (IOException e) {
            LOGGER.log(Level.TRACE,
                       "imds service is not reachable, or timed out for address: " + imdsAddress + ", instance principal "
                               + "strategy is not available.",
                       e);
            return false;
        }
    }
}
