package io.helidon.nima.webclient.api;

import java.util.List;

import io.helidon.builder.api.Prototype;
import io.helidon.config.metadata.Configured;
import io.helidon.config.metadata.ConfiguredOption;
import io.helidon.nima.webclient.spi.ProtocolConfig;
import io.helidon.nima.webclient.spi.ProtocolConfigProvider;
import io.helidon.inject.configdriven.api.ConfigBean;

/**
 * Web client configuration.
 */
@Configured(root = true, prefix = "clients")
@ConfigBean(repeatable = true, wantDefault = true)
@Prototype.Blueprint
interface WebClientConfigBlueprint extends HttpClientConfigBlueprint, Prototype.Factory<WebClient> {
    /**
     * Configuration of client protocols.
     *
     * @return client protocol configurations
     */
    @ConfiguredOption(provider = true, providerType = ProtocolConfigProvider.class)
    @Prototype.Singular
    List<ProtocolConfig> protocolConfigs();

    /**
     * List of HTTP protocol IDs by order of preference. If left empty, all discovered providers will be used, ordered by
     * weight.
     * <p>
     * For example if both HTTP/2 and HTTP/1.1 providers are available (considering HTTP/2 has higher weights), for ALPN
     * we will send h2 and http/1.1 and decide based on response.
     * If TLS is not used, we would attempt an upgrade (or use prior knowledge if configured in {@link #protocolConfigs()}).
     *
     * @return list of HTTP protocol IDs in order of preference
     */
    @Prototype.Singular
    List<String> protocolPreference();
}
