/*
 * Copyright (c) 2024, 2025 Oracle and/or its affiliates.
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

import io.helidon.scheduling.CronInvocation;
import io.helidon.scheduling.FixedRateInvocation;
import io.helidon.scheduling.Scheduling;

@SuppressWarnings("ALL")
class SchedulingSnippets {

    class Snippet1 {

        // tag::snippet_1[]
        @Scheduling.FixedRate(delayBy = "PT5M", value = "PT10M")
        // end::snippet_1[]
        public void methodName() { /* ... */ }
    }

    class Snippet2 {

        // tag::snippet_2[]
        @Scheduling.FixedRate(delayBy = "PT5M", value = "PT10M")
        // end::snippet_2[]
        public void methodName(FixedRateInvocation invocation) { /* ... */ }
    }

    class Snippet3 {
        // tag::snippet_3[]
        @Scheduling.Cron(value = "0 15 8 ? * *", concurrent = false)
        public void methodName() { /* ... */ }
        // end::snippet_3[]
    }

    class Snippet4 {

        // tag::snippet_4[]
        @Scheduling.Cron("0 15 8 ? * *")
        public void methodName(CronInvocation inv) {
            { /* ... */ }
        }
        // end::snippet_4[]
    }

    class Snippet5 {

        // tag::snippet_5[]
        @Scheduling.FixedRate(delayBy = "PT5M", value = "PT10M")
        public void methodName() {
            System.out.println("Every 10 minutes, first invocation 5 minutes after start");
        }
        // end::snippet_5[]
    }

    class Snippet6 {

        // tag::snippet_6[]
        @Scheduling.FixedRate(delayBy = "PT5M", value = "PT10M")
        public void methodName(FixedRateInvocation inv) {
            System.out.println("Method invoked " + inv.description());
        }
        // end::snippet_6[]
    }

    class Snippet7 {

        // tag::snippet_7[]
        @Scheduling.Cron(value = "0 15 8 ? * *", concurrent = false)
        public void methodName() {
            System.out.println("Executer every day at 8:15");
        }
        // end::snippet_7[]
    }

    class Snippet8 {

        // tag::snippet_8[]
        @Scheduling.Cron("0 15 8 ? * *")
        public void methodName(CronInvocation inv) {
            System.out.println("Method invoked " + inv.description());
        }
        // end::snippet_8[]
    }
}
