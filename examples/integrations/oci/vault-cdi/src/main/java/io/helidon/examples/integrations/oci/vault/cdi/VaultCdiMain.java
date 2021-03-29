package io.helidon.examples.integrations.oci.vault.cdi;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import io.helidon.config.yaml.YamlMpConfigSource;
import io.helidon.microprofile.cdi.Main;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.spi.ConfigProviderResolver;
import org.eclipse.microprofile.config.spi.ConfigSource;

public class VaultCdiMain {
    public static void main(String[] args) {
        ConfigProviderResolver configProvider = ConfigProviderResolver.instance();

        Config mpConfig = configProvider.getBuilder()
                .addDefaultSources()
                .withSources(examplesConfig())
                .addDiscoveredSources()
                .addDiscoveredConverters()
                .build();

        // configure
        configProvider.registerConfig(mpConfig, null);

        // start CDI
        Main.main(args);
    }

    private static ConfigSource[] examplesConfig() {
        Path path = Paths.get(System.getProperty("user.home") + "/helidon/conf/examples.yaml");
        if (Files.exists(path)) {
            return new ConfigSource[] {YamlMpConfigSource.create(path)};
        }
        return new ConfigSource[0];
    }
}
