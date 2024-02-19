package io.helidon.service.registry;

import java.util.Objects;
import java.util.Set;

import io.helidon.builder.api.Prototype;
import io.helidon.common.Weighted;
import io.helidon.common.types.TypeName;

class ServiceRegistryConfigSupport {
    private ServiceRegistryConfigSupport() {
    }

    static class CustomMethods {
        private CustomMethods() {
        }

        /**
         * Put an instance of a contract outside of service described services.
         * This will create a "virtual" service descriptor that will not be valid for metadata operations.
         *
         * @param builder  ignored
         * @param contract contract to add a specific instance for
         * @param instance instance of the contract
         */
        @Prototype.BuilderMethod
        static void putContractInstance(ServiceRegistryConfig.BuilderBase<?, ?> builder,
                                        TypeName contract,
                                        Object instance) {
            builder.putServiceInstance(new VirtualDescriptor(contract), instance);
        }

        /**
         * Put an instance of a contract outside of service described services.
         * This will create a "virtual" service descriptor that will not be valid for metadata operations.
         *
         * @param builder  ignored
         * @param contract contract to add a specific instance for
         * @param instance instance of the contract
         */
        @Prototype.BuilderMethod
        static void putContractInstance(ServiceRegistryConfig.BuilderBase<?, ?> builder,
                                        Class<?> contract,
                                        Object instance) {
            putContractInstance(builder, TypeName.create(contract), instance);
        }
    }

    private static class VirtualDescriptor implements GeneratedService.Descriptor<Object> {
        private static final TypeName TYPE = TypeName.create(VirtualDescriptor.class);
        private final Set<TypeName> contracts;
        private final TypeName serviceType;
        private final TypeName descriptorType;

        private VirtualDescriptor(TypeName contract) {
            this.contracts = Set.of(contract);
            this.serviceType = contract;
            this.descriptorType = TypeName.builder(TYPE)
                    .className(TYPE.className() + "_" + contract.className() + "__VirtualDescriptor")
                    .build();
        }

        @Override
        public TypeName serviceType() {
            return serviceType;
        }

        @Override
        public TypeName descriptorType() {
            return descriptorType;
        }

        @Override
        public Set<TypeName> contracts() {
            return contracts;
        }

        @Override
        public double weight() {
            return Weighted.DEFAULT_WEIGHT + 1000;
        }

        @Override
        public int hashCode() {
            return Objects.hash(serviceType);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof VirtualDescriptor that)) {
                return false;
            }
            return Objects.equals(serviceType, that.serviceType);
        }
    }
}
