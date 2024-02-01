package io.helidon.service.inject.api;

import io.helidon.builder.api.Prototype;
import io.helidon.common.types.ElementKind;
import io.helidon.service.registry.Dependency;

final class IpSupport {
    private IpSupport() {
    }

    final class CustomMethods {
        private CustomMethods() {
        }

        /**
         * Return the dependency if it is an instance of {@link io.helidon.service.inject.api.Ip},
         * or create an Ip that is equivalent to the dependency.
         *
         * @param dependency dependency to convert to injection point
         * @return injection point
         */
        @Prototype.FactoryMethod
        static Ip create(Dependency dependency) {
            if (dependency instanceof Ip ip) {
                return ip;
            }
            return Ip.builder()
                    .from(dependency)
                    .elementKind(ElementKind.CONSTRUCTOR)
                    .build();
        }
    }
}
