package io.helidon.nima.common.tls;

import java.net.URI;

import io.helidon.builder.api.Prototype;
import io.helidon.config.metadata.Configured;
import io.helidon.config.metadata.ConfiguredOption;

@Prototype.Blueprint
@Configured
interface CustomTestTlsManagerConfigBlueprint extends TlsManagerConfigBlueprint {

    @ConfiguredOption
    URI uri();

}
