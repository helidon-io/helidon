package io.helidon.integrations.oci;

import java.util.Optional;

import io.helidon.common.LazyValue;
import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.integrations.oci.spi.OciRegion;
import io.helidon.service.registry.Service;

import com.oracle.bmc.Region;

@Service.Provider
@Weight(Weighted.DEFAULT_WEIGHT - 100)
class SdkRegionProvider implements OciRegion {
    private final LazyValue<Optional<Region>> region = LazyValue.create(() -> Optional.ofNullable(Region.getRegionFromImds()));

    @Override
    public Optional<Region> region() {
        return region.get();
    }
}
