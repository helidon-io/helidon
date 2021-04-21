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
package io.helidon.tests.functional.requestscope;

import java.time.temporal.ChronoUnit;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.Priority;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.BeforeDestroyed;
import javax.enterprise.context.Initialized;
import javax.enterprise.event.Observes;
import javax.interceptor.Interceptor;
import javax.ws.rs.ProcessingException;

import org.eclipse.microprofile.faulttolerance.Fallback;
import org.eclipse.microprofile.faulttolerance.Retry;

/**
 * Verifies that FT initializes its internal state correctly when a request scope
 * is already initialized but a request context is not.
 */
@ApplicationScoped
public class StartupServices {

    /**
     * Checked by tests to verify startup was successful.
     */
    public static final AtomicBoolean SUCCESSFUL_STARTUP = new AtomicBoolean(false);

    private static ScheduledExecutorService execServiceApis;

    private static void onStartup(@Observes @Priority(Interceptor.Priority.PLATFORM_AFTER + 101)
                                  @Initialized(ApplicationScoped.class) final Object event, StartupServices self) {
        execServiceApis = Executors.newSingleThreadScheduledExecutor();
        execServiceApis.execute(() -> {
            try {
                self.loadApis();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private static void rightBeforeShutdown(@Observes @BeforeDestroyed(ApplicationScoped.class) final Object event) {
        if (execServiceApis != null) {
            execServiceApis.shutdown();
        }
    }

    @Retry(delay = 1, delayUnit = ChronoUnit.SECONDS,
            maxDuration = 60, durationUnit = ChronoUnit.SECONDS,
            maxRetries = 10, abortOn = javax.ws.rs.ClientErrorException.class)
    @Fallback(fallbackMethod = "loadApisFromDisk")
    protected void loadApis() throws Exception {
        if (SUCCESSFUL_STARTUP.compareAndSet(false, true)) {
            throw new ProcessingException("oops");      // will throw exception once
        }
    }

    protected void loadApisFromDisk() throws Exception {
    }
}
