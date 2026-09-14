/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.integrations.langchain4j.providers.oci.genai;

import java.util.concurrent.atomic.AtomicBoolean;

import io.helidon.service.registry.Service;

@Service.Singleton
final class LifecycleTestModelShutdownObserver {
    private static final AtomicBoolean STOPPED_WITH_OPEN_MODEL = new AtomicBoolean();

    private final LifecycleTestModel model;

    LifecycleTestModelShutdownObserver(@Service.Named("ordered") LifecycleTestModel model) {
        this.model = model;
    }

    static void reset() {
        STOPPED_WITH_OPEN_MODEL.set(false);
    }

    static boolean stoppedWithOpenModel() {
        return STOPPED_WITH_OPEN_MODEL.get();
    }

    @Service.PreDestroy
    void preDestroy() {
        model.assertOpen();
        STOPPED_WITH_OPEN_MODEL.set(true);
    }
}
