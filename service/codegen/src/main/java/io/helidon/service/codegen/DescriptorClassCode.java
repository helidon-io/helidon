package io.helidon.service.codegen;

import java.util.Set;

import io.helidon.codegen.ClassCode;
import io.helidon.common.types.TypeName;

/**
 * New service descriptor metadata with its class code.
 */
public interface DescriptorClassCode {
    /**
     * New source code information.
     *
     * @return class code
     */
    ClassCode classCode();

    /**
     * Type of registry of this descriptor.
     *
     * @return registry type
     */
    String registryType();

    /**
     * Weight of the new descriptor.
     *
     * @return weight
     */
    double weight();

    /**
     * Contracts the described service implements/provides.
     *
     * @return contracts of the service
     */
    Set<TypeName> contracts();
}
