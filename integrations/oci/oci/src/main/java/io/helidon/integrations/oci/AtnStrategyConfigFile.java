package io.helidon.integrations.oci;

import java.io.IOException;
import java.lang.System.Logger.Level;
import java.util.Optional;

import io.helidon.common.LazyValue;
import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.integrations.oci.spi.OciAtnStrategy;
import io.helidon.service.registry.Service;

import com.oracle.bmc.ConfigFileReader;
import com.oracle.bmc.auth.AbstractAuthenticationDetailsProvider;
import com.oracle.bmc.auth.ConfigFileAuthenticationDetailsProvider;

/**
 * Config file based authentication strategy, uses the {@link com.oracle.bmc.auth.ConfigFileAuthenticationDetailsProvider}.
 */
@Weight(Weighted.DEFAULT_WEIGHT - 20)
@Service.Provider
class AtnStrategyConfigFile implements OciAtnStrategy {
    static final String DEFAULT_PROFILE_NAME = "DEFAULT";
    static final String STRATEGY = "config_file";

    private static final System.Logger LOGGER = System.getLogger(AtnStrategyConfigFile.class.getName());

    private final LazyValue<Optional<AbstractAuthenticationDetailsProvider>> provider;

    AtnStrategyConfigFile(OciConfig config) {
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
            // there are two options to override - the path to config file, and the profile
            var strategyConfig = config.configFileStrategyConfig();
            String profile = strategyConfig.map(ConfigFileStrategyConfigBlueprint::profile)
                    .orElse(DEFAULT_PROFILE_NAME);
            String configFilePath = strategyConfig.flatMap(ConfigFileStrategyConfigBlueprint::path)
                    .orElse(null);

            try {
                ConfigFileReader.ConfigFile configFile;
                if (configFilePath == null) {
                    configFile = ConfigFileReader.parseDefault(profile);
                } else {
                    configFile = ConfigFileReader.parse(configFilePath, profile);
                }
                return Optional.of(new ConfigFileAuthenticationDetailsProvider(configFile));
            } catch (IOException e) {
                if (LOGGER.isLoggable(Level.TRACE)) {
                    LOGGER.log(Level.TRACE, "Cannot parse config file. Location: " + configFilePath + ", profile: " + profile, e);
                }
                return Optional.empty();
            }
        });
    }
}
