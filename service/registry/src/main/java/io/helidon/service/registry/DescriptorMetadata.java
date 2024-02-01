package io.helidon.service.registry;

import java.util.Set;

import io.helidon.common.types.TypeName;

public interface DescriptorMetadata {
    String REGISTRY_TYPE_CORE = "core";

    String registryType();

    TypeName descriptorType();

    Set<TypeName> contracts();

    double weight();

    GeneratedService.Descriptor<?> descriptor();
}
