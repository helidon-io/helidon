package io.helidon.service.codegen;

import java.util.Set;

import io.helidon.codegen.ClassCode;
import io.helidon.common.types.TypeName;

record DescriptorClassCodeImpl(ClassCode classCode,
                               String registryType,
                               double weight,
                               Set<TypeName> contracts) implements DescriptorClassCode {
}
