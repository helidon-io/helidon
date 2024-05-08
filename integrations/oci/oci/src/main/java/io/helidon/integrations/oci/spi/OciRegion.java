package io.helidon.integrations.oci.spi;

import java.util.Optional;

import io.helidon.service.registry.Service;

import com.oracle.bmc.Region;

@Service.Contract
public interface OciRegion {
    Optional<Region> region();
}
