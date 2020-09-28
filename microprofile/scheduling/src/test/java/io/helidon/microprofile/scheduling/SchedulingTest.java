/*
 * Copyright (c)  2020 Oracle and/or its affiliates.
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

package io.helidon.microprofile.scheduling;

import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import io.helidon.microprofile.tests.junit5.AddBean;
import io.helidon.microprofile.tests.junit5.AddBeans;
import io.helidon.microprofile.tests.junit5.AddExtension;
import io.helidon.microprofile.tests.junit5.AddExtensions;
import io.helidon.microprofile.tests.junit5.DisableDiscovery;
import io.helidon.microprofile.tests.junit5.HelidonTest;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.lessThan;

import org.junit.jupiter.api.Test;

@HelidonTest
@DisableDiscovery
@AddBeans({
        @AddBean(ScheduledBean.class)
})
@AddExtensions({
        @AddExtension(SchedulingCdiExtension.class),
})
public class SchedulingTest {

    static final long TWO_SEC_MILLIS = 2 * 1000L;

    @Inject
    ScheduledBean scheduledBean;

    @Test
    void executedEvery2Sec() throws InterruptedException {
        assertThat("Scheduled method expected to be invoked at least twice",
                scheduledBean.getCountDownLatch().await(5, TimeUnit.SECONDS));
        assertDuration(scheduledBean.getDuration(), 200);
    }

    private void assertDuration(long duration, long allowedDiscrepancy) {
        String durationString = "Expected duration is 2 sec, but was " + ((float) duration / 1000) + "sec";
        assertThat(durationString, duration, greaterThan(TWO_SEC_MILLIS - allowedDiscrepancy));
        assertThat(durationString, duration, lessThan(TWO_SEC_MILLIS + allowedDiscrepancy));
    }
}
