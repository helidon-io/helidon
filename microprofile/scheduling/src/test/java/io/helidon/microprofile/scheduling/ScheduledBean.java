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

import java.time.LocalTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import javax.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ScheduledBean {

    private static final Logger LOGGER = Logger.getLogger(ScheduledBean.class.getName());

    final CountDownLatch countDownLatch = new CountDownLatch(2);


    volatile long duration = 0;
    volatile long stamp = 0;

    public CountDownLatch getCountDownLatch() {
        return countDownLatch;
    }

    public long getDuration() {
        return duration;
    }

    @Scheduled("0/2 * * * * ? *")
    public void test2sec() {
        countDownLatch.countDown();
        duration = System.currentTimeMillis() - stamp;
        stamp = System.currentTimeMillis();
        LOGGER.fine(() -> "Executed at " + LocalTime.now().toString());
    }

}
