/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.common.resumable;

import java.lang.management.ManagementFactory;

/**
 * Allows notification of resumable resources before suspend and after resume.
 * Instances of classes implementing {@code Resumable} and registered with
 * {@code ResumableSupport.get().register(resumableResource)} are notified
 * before suspend and after resume by underlying resumable implementation.
 */
public interface ResumableSupport {

    /**
     * Gets resumable support singleton.
     * @return singleton
     */
    static ResumableSupport get() {
        return ResumableLookup.get();
    }

    /**
     * Registers a {@code Resumable} as a listener for suspend and resume.
     *
     * @param resumable {@code Resumable} to be registered.
     * @throws NullPointerException if {@code resource} is {@code null}
     */
    void register(Resumable resumable);

    /**
     * Requests checkpoint and returns upon a successful resume.
     */
    void checkpointResume();

    /**
     * Requests checkpoint and returns upon a successful resume when {@code -Dio.helidon.crac.checkpoint=onStartup} is set.
     */
    default void checkpointResumeOnStartup(){
        if ("onStart".equalsIgnoreCase(System.getProperty("io.helidon.resumable.checkpoint"))) {
            checkpointResume();
        }
    }

    /**
     * Returns the time since the Java virtual machine resume was initiated.
     * If the machine was not resumed, returns -1.
     *
     * @return uptime of the Java virtual machine in milliseconds.
     * @see java.lang.management.RuntimeMXBean#getStartTime()
     */
    long uptimeSinceResume();

    /**
     * Returns the time when the Java virtual machine resume was initiated.
     * The value is the number of milliseconds since the start of the epoch.
     * If the machine was not resumed, returns -1.
     *
     * @return start time of the Java virtual machine in milliseconds.
     * @see java.lang.management.RuntimeMXBean#getUptime()
     */
    long resumeTime();

    /**
     * Returns either uptime since resume, or since JVM start if not resumed.
     * @return actual uptime in milliseconds
     */
    default long uptime() {
        long uptimeSinceResume = uptimeSinceResume();
        return uptimeSinceResume == -1 ? ManagementFactory.getRuntimeMXBean().getUptime() : uptimeSinceResume;
    }
}
