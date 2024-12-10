package io.helidon.common.mapper;

import java.util.List;
import java.util.function.Supplier;

import io.helidon.common.mapper.spi.MapperProvider;
import io.helidon.service.registry.Service;

@Service.Singleton
class MappersFactory implements Supplier<Mappers> {
    private final List<MapperProvider> mapperProviders;
    private final List<Mapper<?, ?>> mappers;

    @Service.Inject
    MappersFactory(List<MapperProvider> mapperProviders, List<Mapper<?, ?>> mappers) {
        this.mapperProviders = mapperProviders;
        this.mappers = mappers;
    }

    @Override
    public Mappers get() {
        return Mappers.builder()
                .mapperProvidersDiscoverServices(false)
                .mappersDiscoverServices(false)
                .update(it -> mappers.forEach(it::addMapper))
                .update(it -> mapperProviders.forEach(it::addMapperProvider))
                .build();
    }
}
