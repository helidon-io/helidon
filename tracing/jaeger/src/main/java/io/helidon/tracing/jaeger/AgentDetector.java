/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.tracing.jaeger;

import java.util.logging.Logger;

import io.opentelemetry.context.Context;

/**
 *  Utility class used to check if there is OpenTelemetry Java Agent running along with Helidon.
 */
class AgentDetector {

    static final Logger LOGGER = Logger.getLogger(AgentDetector.class.getName());

    private AgentDetector(){
        //empty constructor for a utility class.
    }

    /**
     * Check if Java Agent is running.
     *
     * @return boolean
     */
    static boolean checkAgent(){

        // First iteration: check if context contains class instrumented by an agent.
        if ((Context.current()).getClass().getName().contains("agent")){
            // Global telemetry already set
            LOGGER.info("OpenTelemetry Agent detected.");
            return true;
        }

        // Second iteration: check if there are properties set by
        for (Object o : System.getProperties().keySet()) {
            if (((String) o).contains("io.opentelemetry.javaagent")){
                // Global telemetry already set
                LOGGER.info("OpenTelemetry Agent detected.");
                return true;
            }
        }

        return false;
    }
}
