/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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
package io.helidon.microprofile.grpc.metrics;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.metrics.annotation.Counted;
import org.eclipse.microprofile.metrics.annotation.SimplyTimed;

@ApplicationScoped
@Counted
public class NonGrpcMetricAnnotatedBean {

    static final String MESSAGE_SIMPLE_TIMER = "messageSimpleTimer";

    // Do not add other metrics annotations to this method!
    public String message() {
        return "Hello World";
    }

    @SimplyTimed(name = MESSAGE_SIMPLE_TIMER, absolute = true)
    public String messageWithArg(String input){
        return "Hello World, " + input;
    }
}
