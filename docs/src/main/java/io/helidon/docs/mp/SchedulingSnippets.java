/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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
package io.helidon.docs.mp;

import java.util.concurrent.TimeUnit;

import io.helidon.microprofile.scheduling.FixedRate;
import io.helidon.microprofile.scheduling.Scheduled;
import io.helidon.scheduling.CronInvocation;
import io.helidon.scheduling.FixedRateInvocation;

@SuppressWarnings("ALL")
class SchedulingSnippets {

    class Snippet1 {

        // tag::snippet_1[]
        @FixedRate(initialDelay = 5, value = 10, timeUnit = TimeUnit.MINUTES)
        // end::snippet_1[]
        public void methodName() { /* ... */ }
    }

    class Snippet2 {

        // tag::snippet_2[]
        @FixedRate(initialDelay = 5, value = 10, timeUnit = TimeUnit.MINUTES)
        // end::snippet_2[]
        public void methodName() { /* ... */ }
    }

    class Snippet3 {
        // tag::snippet_3[]
        @Scheduled(value = "0 15 8 ? * *", concurrentExecution = false)
        public void methodName() { /* ... */ }
        // end::snippet_3[]
    }

    class Snippet4 {

        // tag::snippet_4[]
        @Scheduled("0 15 8 ? * *")
        public void methodName(CronInvocation inv) {
            { /* ... */ }
        }
        // end::snippet_4[]
    }

    class Snippet5 {

        // tag::snippet_5[]
        @FixedRate(initialDelay = 5, value = 10, timeUnit = TimeUnit.MINUTES)
        public void methodName() {
            System.out.println("Every 10 minutes, first invocation 5 minutes after start");
        }
        // end::snippet_5[]
    }

    class Snippet6 {

        // tag::snippet_6[]
        @FixedRate(initialDelay = 5, value = 10, timeUnit = TimeUnit.MINUTES)
        public void methodName(FixedRateInvocation inv) {
            System.out.println("Method invoked " + inv.description());
        }
        // end::snippet_6[]
    }

    class Snippet7 {

        // tag::snippet_7[]
        @Scheduled(value = "0 15 8 ? * *", concurrentExecution = false)
        public void methodName() {
            System.out.println("Executer every day at 8:15");
        }
        // end::snippet_7[]
    }

    class Snippet8 {

        // tag::snippet_8[]
        @Scheduled("0 15 8 ? * *")
        public void methodName(CronInvocation inv) {
            System.out.println("Method invoked " + inv.description());
        }
        // end::snippet_8[]
    }
}
