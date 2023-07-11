import io.helidon.common.features.api.Feature;
import io.helidon.common.features.api.HelidonFlavor;

/**
 * WebClient API and HTTP/1.1 implementation.
 */
@Feature(value = "HTTP/1.1",
         description = "Web Client HTTP/1.1 support",
         in = HelidonFlavor.NIMA,
         invalidIn = HelidonFlavor.SE,
         path = {"Web Client", "HTTP/1.1"}
)
module io.helidon.nima.webclient.http1 {
    uses io.helidon.nima.webclient.spi.SourceHandlerProvider;
    requires static io.helidon.common.features.api;
    // @ConfiguredOption etc
    requires static io.helidon.config.metadata;

    requires transitive io.helidon.nima.webclient.api;
    // @Builder - interfaces are a runtime dependency
    requires io.helidon.builder.api;

    exports io.helidon.nima.webclient.http1;

    provides io.helidon.nima.webclient.spi.HttpClientSpiProvider
            with io.helidon.nima.webclient.http1.Http1ClientSpiProvider;
    provides io.helidon.nima.webclient.spi.ProtocolConfigProvider
            with io.helidon.nima.webclient.http1.Http1ProtocolConfigProvider;
}