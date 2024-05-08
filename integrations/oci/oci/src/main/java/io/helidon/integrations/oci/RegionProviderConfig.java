package io.helidon.integrations.oci;

import java.util.Optional;

import io.helidon.common.LazyValue;
import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.common.config.Config;
import io.helidon.integrations.oci.spi.OciRegion;
import io.helidon.service.registry.Service;

import com.oracle.bmc.Region;

@Service.Provider
@Weight(Weighted.DEFAULT_WEIGHT - 10)
class RegionProviderConfig implements OciRegion {
    private final LazyValue<Optional<Region>> region;

    RegionProviderConfig(Config config) {
        this.region = LazyValue.create(() -> config.get("oci.region")
                .asString()
                .map(Region::fromRegionCodeOrId));
    }

    @Override
    public Optional<Region> region() {
        return region.get();
    }
}
