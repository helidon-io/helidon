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

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;

@SuppressWarnings("ALL")
class ExecuteOnSnippets {

    // stub
    static class PiDigitCalculator {
        static int nthDigitOfPi(int n) {
            return 0;
        }
    }

    // tag::snippet_1[]
    public class MyPlaformBean {

        @ExecuteOn(ThreadType.PLATFORM)
        int cpuIntensive(int n) {
            return PiDigitCalculator.nthDigitOfPi(n);
        }
    }
    // end::snippet_1[]

    // tag::snippet_2[]
    public class MyExecutorBean {

        @ExecuteOn(value = ThreadType.EXECUTOR, executorName = "my-executor")
        int cpuIntensive(int n) {
            return PiDigitCalculator.nthDigitOfPi(n);
        }

        @Produces
        @Named("my-executor")
        ExecutorService myExecutor() {
            return Executors.newFixedThreadPool(2);
        }
    }
    // end::snippet_2[]

    // tag::snippet_3[]
    public class MyVirtualBean {

        @ExecuteOn(VIRTUAL)
        void someTask() {
            // run task on virtual thread
        }
    }
    // end::snippet_3[]
}
