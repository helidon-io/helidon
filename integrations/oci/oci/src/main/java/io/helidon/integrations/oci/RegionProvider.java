package io.helidon.integrations.oci;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import io.helidon.integrations.oci.spi.OciRegion;
import io.helidon.service.registry.Service;

import com.oracle.bmc.Region;

@Service.Provider
@Service.ExternalContracts(Region.class)
class RegionProvider implements Supplier<Region> {
    private final List<OciRegion> regionProviders;

    RegionProvider(List<OciRegion> regionProviders) {
        this.regionProviders = regionProviders;
    }

    @Override
    public Region get() {
        for (OciRegion regionProvider : regionProviders) {
            Optional<Region> region = regionProvider.region();
            if (region.isPresent()) {
                return region.get();
            }
        }
        throw new RuntimeException("Cannot discover OCI region");
    }
}
