package io.helidon.config.mp.spi;

import java.util.List;
import java.util.Set;

import io.helidon.config.Config;

import org.eclipse.microprofile.config.spi.ConfigSource;

/**
 * Java Service loader interface for Meta-config providers.
 */
public interface MpMetaConfigProvider {
    /**
     * Set of supported types for a MicroProfile meta-config provider.
     *
     * @return meta-config provider types
     */
    Set<String> supportedTypes();

    /**
     * Create a list of configuration sources from a meta-config type.
     *
     * @param type type of the config source
     * @param metaConfig configuration properties of a meta-config type
     * @param profile name of the profile to use or null if not used
     *
     * @return list of config sources
     */
    List<? extends ConfigSource> create(String type, Config metaConfig, String profile);

    /*
    impl: metaConfig.get("classpath").asString().ifPresent(...)
     */
}
