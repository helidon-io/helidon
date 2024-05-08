package io.helidon.integrations.oci;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import io.helidon.common.LazyValue;
import io.helidon.common.config.ConfigException;
import io.helidon.integrations.oci.spi.OciAtnStrategy;
import io.helidon.service.registry.Service;

import com.oracle.bmc.auth.AbstractAuthenticationDetailsProvider;

@Service.Provider
@Service.ExternalContracts(AbstractAuthenticationDetailsProvider.class)
class AtnDetailsProvider implements Supplier<AbstractAuthenticationDetailsProvider> {

    private final LazyValue<AbstractAuthenticationDetailsProvider> provider;

    AtnDetailsProvider(OciConfig ociConfig, List<OciAtnStrategy> atnDetailsProviders) {
        String chosenStrategy = ociConfig.atnStrategy();
        LazyValue<AbstractAuthenticationDetailsProvider> providerLazyValue = null;

        if (OciConfigBlueprint.STRATEGY_AUTO.equals(chosenStrategy)) {
            // auto, chose from existing
            providerLazyValue = LazyValue.create(() -> {
                List<String> strategies = new ArrayList<>();
                for (OciAtnStrategy atnDetailsProvider : atnDetailsProviders) {
                    Optional<AbstractAuthenticationDetailsProvider> provider = atnDetailsProvider.provider();
                    if (provider.isPresent()) {
                        return provider.get();
                    }
                    strategies.add(atnDetailsProvider.strategy());
                }
                throw new RuntimeException("Cannot discover OCI Authentication Details Provider, none of the strategies"
                                                   + " returned a valid provider. Supported strategies: " + strategies);
            });
        } else {
            List<String> strategies = new ArrayList<>();

            for (OciAtnStrategy atnDetailsProvider : atnDetailsProviders) {
                if (chosenStrategy.equals(atnDetailsProvider.strategy())) {
                    providerLazyValue = LazyValue.create(() -> atnDetailsProvider.provider().orElseThrow(() ->
                                                                                                                 new ConfigException(
                                                                                                                         "Strategy \"" + chosenStrategy + "\" did not provide an authentication provider, "
                                                                                                                                 + "yet it is requested through configuration.")));
                    break;
                }
                strategies.add(atnDetailsProvider.strategy());
            }

            if (providerLazyValue == null) {
                throw new ConfigException("There is a strategy chosen for OCI authentication: \"" + chosenStrategy
                                                  + "\", yet there is not provider that can provide that strategy. Supported "
                                                  + "strategies: " + strategies);
            }
        }

        this.provider = providerLazyValue;
    }

    @Override
    public AbstractAuthenticationDetailsProvider get() {
        return provider.get();
    }
}
