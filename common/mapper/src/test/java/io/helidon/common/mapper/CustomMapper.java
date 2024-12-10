package io.helidon.common.mapper;

import io.helidon.common.GenericType;
import io.helidon.common.Size;
import io.helidon.service.registry.Service;

@Service.Singleton
class CustomMapper implements Mapper<String, Size> {
    @Override
    public Size map(String string) {
        return Size.parse(string);
    }

    @Override
    public GenericType<String> sourceType() {
        return GenericType.STRING;
    }

    @Override
    public GenericType<Size> targetType() {
        return GenericType.create(Size.class);
    }
}
