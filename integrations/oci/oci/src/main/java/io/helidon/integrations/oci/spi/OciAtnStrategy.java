package io.helidon.integrations.oci.spi;

import java.util.Optional;

import io.helidon.service.registry.Service;

import com.oracle.bmc.auth.AbstractAuthenticationDetailsProvider;

@Service.Contract
public interface OciAtnStrategy {
    String strategy();
    Optional<AbstractAuthenticationDetailsProvider> provider();
}
