package io.helidon.nima.common.tls;

import io.helidon.builder.api.Prototype;
import io.helidon.config.metadata.Configured;
import io.helidon.config.metadata.ConfiguredOption;

@Prototype.Blueprint
@Configured
interface TlsManagerConfigBlueprint extends Prototype.Factory<TlsManager> {

//    @Prototype.Singular
//    @ConfiguredOption(provider = true,
//                      providerType = TlsManagerProvider.class)
//    List<TlsManagerService> tlsManager();

    @ConfiguredOption
    String schedule();

}
