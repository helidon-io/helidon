package io.helidon.integrations.oci;

import java.util.Optional;
import java.util.function.Supplier;

import io.helidon.common.LazyValue;
import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.integrations.oci.spi.OciRegion;
import io.helidon.service.registry.Service;

import com.oracle.bmc.Region;
import com.oracle.bmc.auth.AbstractAuthenticationDetailsProvider;

@Service.Provider
@Weight(Weighted.DEFAULT_WEIGHT - 20)
class RegionProviderAtnStrategy implements OciRegion {
    private final LazyValue<Optional<Region>> region;

    RegionProviderAtnStrategy(Supplier<AbstractAuthenticationDetailsProvider> atnProvider) {

        this.region = LazyValue.create(() -> {
            var provider = atnProvider.get();
            if (provider instanceof RegionProvider regionProvider) {
                return Optional.of(regionProvider.get());
            }
            return Optional.empty();
        });
    }

    @Override
    public Optional<Region> region() {
        return region.get();
    }
}
