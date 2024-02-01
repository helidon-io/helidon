package io.helidon.service.registry;

import java.util.Set;

import io.helidon.common.types.TypeName;
import io.helidon.service.registry.GeneratedService.Descriptor;

interface ServiceMetadata {
    double weight();
    Set<TypeName> contracts();
    Descriptor<?> descriptor();
}
