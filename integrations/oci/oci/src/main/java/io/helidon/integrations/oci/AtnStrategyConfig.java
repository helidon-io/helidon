package io.helidon.integrations.oci;

import java.util.Optional;

import io.helidon.common.LazyValue;
import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.common.configurable.Resource;
import io.helidon.integrations.oci.spi.OciAtnStrategy;
import io.helidon.service.registry.Service;

import com.oracle.bmc.Region;
import com.oracle.bmc.auth.AbstractAuthenticationDetailsProvider;
import com.oracle.bmc.auth.SimpleAuthenticationDetailsProvider;
import com.oracle.bmc.auth.SimplePrivateKeySupplier;

/**
 * Config based authentication strategy, uses the {@link com.oracle.bmc.auth.SimpleAuthenticationDetailsProvider}.
 */
@Weight(Weighted.DEFAULT_WEIGHT - 10)
@Service.Provider
class AtnStrategyConfig implements OciAtnStrategy {
    static final String STRATEGY = "config";

    private final LazyValue<Optional<AbstractAuthenticationDetailsProvider>> provider;

    AtnStrategyConfig(OciConfig config) {
        provider = config.configStrategyConfig()
                .map(configStrategyConfigBlueprint -> LazyValue.create(() -> {
                    return Optional.of(createProvider(configStrategyConfigBlueprint));
                }))
                .orElseGet(() -> LazyValue.create(Optional.empty()));
    }

    @Override
    public String strategy() {
        return STRATEGY;
    }

    @Override
    public Optional<AbstractAuthenticationDetailsProvider> provider() {
        return provider.get();
    }

    private static AbstractAuthenticationDetailsProvider createProvider(ConfigStrategyConfigBlueprint config) {
        Region region = Region.fromRegionCodeOrId(config.region());

        var builder = SimpleAuthenticationDetailsProvider.builder();

        // private key may be provided through different means
        if (config.privateKey().isPresent()) {
            // as a resource (classpath, file system, base64, plain text)
            Resource resource = config.privateKey().get();
            builder.privateKeySupplier(resource::stream);
        } else {
            // or as the default location in user.home/.oci/oic_api_key.pem
            String keyFile = System.getProperty("user.home");
            if (keyFile == null) {
                keyFile = "/";
            } else {
                if (!keyFile.endsWith("/")) {
                    keyFile = keyFile + "/";
                }
            }
            keyFile = keyFile + ".oci/oci_api_key.pem";

            builder.privateKeySupplier(new SimplePrivateKeySupplier(keyFile));
        }

        return builder.region(region)
                .tenantId(config.tenantId())
                .userId(config.userId())
                .fingerprint(config.fingerprint())
                .passphraseCharacters(config.passphrase())
                .build();

    }
}
