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
package io.helidon.docs.se;

import java.util.concurrent.TimeUnit;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.scheduling.Cron;
import io.helidon.scheduling.FixedRate;

@SuppressWarnings("ALL")
class SchedulingSnippets {

    void snippet_1() {
        // tag::snippet_1[]
        FixedRate.builder()
                .delay(10)
                .initialDelay(5)
                .timeUnit(TimeUnit.MINUTES)
                .task(inv -> System.out.println("Every 10 minutes, first invocation 5 minutes after start"))
                .build();
        // end::snippet_1[]
    }

    void snippet_2() {
        // tag::snippet_2[]
        FixedRate.builder()
                .delay(10)
                .task(inv -> System.out.println("Method invoked " + inv.description()))
                .build();
        // end::snippet_2[]
    }

    void snippet_3() {
        // tag::snippet_3[]
        Cron.builder()
                .expression("0 15 8 ? * *")
                .task(inv -> System.out.println("Executer every day at 8:15"))
                .build();
        // end::snippet_3[]
    }

    void snippet_4() {
        // tag::snippet_4[]
        FixedRate.builder()
                .config(Config.create(() -> ConfigSources.create(
                        """
                                delay: 4
                                delay-type: SINCE_PREVIOUS_END
                                initial-delay: 1
                                time-unit: SECONDS
                                """,
                        MediaTypes.APPLICATION_X_YAML)))
                .task(inv -> System.out.println("Every 4 minutes, first invocation 1 minutes after start"))
                .build();
        // end::snippet_4[]
    }

    void snippet_5() {
        // tag::snippet_5[]
        FixedRate.builder()
                .delay(10)
                .initialDelay(5)
                .timeUnit(TimeUnit.MINUTES)
                .task(inv -> System.out.println("Every 10 minutes, first invocation 5 minutes after start"))
                .build();
        // end::snippet_5[]
    }

    void snippet_6() {
        // tag::snippet_6[]
        FixedRate.builder()
                .delay(10)
                .task(inv -> System.out.println("Method invoked " + inv.description()))
                .build();
        // end::snippet_6[]
    }
}
