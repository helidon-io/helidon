package io.helidon.examples.nima.pico;

import io.helidon.config.Config;

import jakarta.inject.Provider;
import jakarta.inject.Singleton;

/**
 * This service will be part of Níma on Pico module.
 * It may use pico to get config sources exposed through pico.
 */
@Singleton
public class ConfigService implements Provider<Config> {
    @Override
    public Config get() {
        return Config.create();
    }
}
