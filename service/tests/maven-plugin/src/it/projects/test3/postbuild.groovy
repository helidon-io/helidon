/*
 * Copyright (c) 2022, 2026 Oracle and/or its affiliates.
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

import io.helidon.service.tests.inject.maven.plugin.ProjectsTestIT
import org.junit.platform.engine.TestExecutionResult
import org.junit.platform.testkit.engine.EngineTestKit

import static org.junit.platform.engine.discovery.DiscoverySelectors.selectMethod

def currentThread = Thread.currentThread()
def originalContextClassLoader = currentThread.getContextClassLoader()

try {
    currentThread.setContextClassLoader(getClass().getClassLoader())

    //noinspection GroovyAssignabilityCheck,GrUnresolvedAccess
    def results = EngineTestKit.engine("junit-jupiter")
            .selectors(selectMethod(ProjectsTestIT.class, "test3"))
            .execute()

    def allEvents = results.allEvents()
    def failures = allEvents.stream()
            .flatMap { event -> event.getPayload(TestExecutionResult.class).stream() }
            .flatMap { result -> result.getThrowable().stream() }
            .toList()

    if (!failures.isEmpty()) {
        throw failures[0]
    }

    results.testEvents().assertStatistics { stats -> stats
            .started(1)
            .succeeded(1)
            .failed(0)
            .aborted(0)
            .skipped(0)
    }

    true
} finally {
    currentThread.setContextClassLoader(originalContextClassLoader)
}
