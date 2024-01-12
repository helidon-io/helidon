package io.helidon.inject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import io.helidon.common.types.TypeName;
import io.helidon.inject.service.Injection;
import io.helidon.inject.service.Qualifier;
import io.helidon.inject.service.ServiceInfo;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;

class ScopeServicesFactoryTest {
    @Test
    void testComparator() {
        /*
        Expected order:
        lowest run level
        highest weight
        no qualifiers
        service type name
         */
        TypeName firstType = TypeName.builder().packageName("io.helidon")
                .className("Aclass")
                .build();
        TypeName secondType = TypeName.builder().packageName("io.helidon")
                .className("Bclass")
                .build();

        TestServiceInfo first = new TestServiceInfo(secondType, 10, 102);
        TestServiceInfo second = new TestServiceInfo(firstType, 10, 102, Qualifier.createNamed("name"));
        TestServiceInfo third = new TestServiceInfo(secondType, 10, 101);
        TestServiceInfo fourth = new TestServiceInfo(firstType, 11, 102);
        TestServiceInfo fifth = new TestServiceInfo(secondType, 11, 100);
        TestServiceInfo sixth = new TestServiceInfo(firstType, 20, 102);
        TestServiceInfo seventh = new TestServiceInfo(firstType, 20, 102, Qualifier.createNamed("name"));
        TestServiceInfo eight = new TestServiceInfo(secondType, 20, 102, Qualifier.createNamed("name"));
        List<ServiceInfo> services = new ArrayList<>();
        services.add(first);
        services.add(second);
        services.add(third);
        services.add(fourth);
        services.add(fifth);
        services.add(sixth);
        services.add(seventh);
        services.add(eight);

        Collections.shuffle(services);
        services.sort(ScopeServicesFactory.EAGER_SERVICE_COMPARATOR);
        assertThat(services, contains(first, second, third, fourth, fifth, sixth, seventh, eight));

        Collections.shuffle(services);
        services.sort(ScopeServicesFactory.EAGER_SERVICE_COMPARATOR);
        assertThat(services, contains(first, second, third, fourth, fifth, sixth, seventh, eight));
    }

    private static class TestServiceInfo implements ServiceInfo {
        private final TypeName serviceType;
        private final int runLevel;
        private final double weight;
        private final Set<Qualifier> qualifiers;

        private TestServiceInfo(TypeName serviceType, int runLevel, double weight, Qualifier... qualifiers) {
            this.serviceType = serviceType;
            this.runLevel = runLevel;
            this.weight = weight;
            this.qualifiers = Set.of(qualifiers);
        }

        @Override
        public TypeName serviceType() {
            return serviceType;
        }

        @Override
        public TypeName scope() {
            return Injection.Singleton.TYPE_NAME;
        }

        @Override
        public Set<Qualifier> qualifiers() {
            return qualifiers;
        }

        @Override
        public double weight() {
            return weight;
        }

        @Override
        public int runLevel() {
            return runLevel;
        }

        @Override
        public String toString() {
            return "TestServiceInfo{" +
                    "serviceType=" + serviceType +
                    ", runLevel=" + runLevel +
                    ", weight=" + weight +
                    ", qualifiers=" + qualifiers +
                    '}';
        }
    }
}