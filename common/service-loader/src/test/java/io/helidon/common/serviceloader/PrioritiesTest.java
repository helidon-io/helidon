/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.helidon.common.serviceloader;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Priority;

import io.helidon.common.Prioritized;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class PrioritiesTest {
    @Test
    void testSort() {
        List<Service> services = new ArrayList<>(5);
        services.add(new DefaultPriority());
        services.add(new VeryLowPriority());
        services.add(new LowestPriority());
        services.add(new HigherPriority());
        services.add(new LowerPriority());

        Priorities.sort(services, 100);

        validate(services);
    }

    @Test
    void testComparator() {
        List<Service> services = new ArrayList<>(5);
        // intentionally different order than in other methods, to make sure it is not working "by accident"
        services.add(new LowestPriority());
        services.add(new LowerPriority());
        services.add(new VeryLowPriority());
        services.add(new HigherPriority());
        services.add(new DefaultPriority());

        services.sort(Priorities.priorityComparator(100));

        validate(services);
    }

    private void validate(List<Service> services) {
        assertThat("There must be 5 services in the list", services.size(), is(5));
        assertThat(services.get(0).getIt(), is(HigherPriority.IT));
        assertThat(services.get(1).getIt(), is(LowerPriority.IT));
        assertThat(services.get(2).getIt(), is(DefaultPriority.IT));
        assertThat(services.get(3).getIt(), is(VeryLowPriority.IT));
        assertThat(services.get(4).getIt(), is(LowestPriority.IT));
    }

    private interface Service {
        String getIt();
    }

    @Priority(1)
    private static class HigherPriority implements Service {
        private static final String IT = "higher";

        @Override
        public String getIt() {
            return IT;
        }
    }

    @Priority(2)
    private static class LowerPriority implements Service {
        private static final String IT = "lower";

        @Override
        public String getIt() {
            return IT;
        }
    }

    private static class DefaultPriority implements Service {
        private static final String IT = "default";

        @Override
        public String getIt() {
            return IT;
        }
    }

    @Priority(101)
    private static class VeryLowPriority implements Service {
        private static final String IT = "veryLow";

        @Override
        public String getIt() {
            return IT;
        }
    }

    private static class LowestPriority implements Service, Prioritized {
        private static final String IT = "lowest";

        @Override
        public String getIt() {
            return IT;
        }

        @Override
        public int priority() {
            return 1000;
        }
    }
}
