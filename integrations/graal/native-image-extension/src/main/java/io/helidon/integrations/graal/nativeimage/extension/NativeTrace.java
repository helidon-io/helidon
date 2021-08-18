/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

package io.helidon.integrations.graal.nativeimage.extension;

import java.util.function.Supplier;

class NativeTrace {
    private static final boolean TRACE_PARSING = NativeConfig.option("reflection.trace-parsing", false);
    private static final boolean TRACE = NativeConfig.option("reflection.trace", false);

    void parsing(Supplier<String> message) {
        if (TRACE_PARSING) {
            System.out.println(message.get());
        }
    }

    void parsing(Supplier<String> message, Throwable e) {
        if (TRACE_PARSING) {
            System.out.println(message.get());
            e.printStackTrace();
        }
    }

    void trace(Supplier<String> message) {
        if (TRACE) {
            System.out.println(message.get());
        }
    }

    void section(Supplier<String> message) {
        if (TRACE) {
            System.out.println("***********************************");
            System.out.println("** " + message.get());
            System.out.println("***********************************");
        }
    }
}
