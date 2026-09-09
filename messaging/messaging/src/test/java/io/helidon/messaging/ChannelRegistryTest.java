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

package io.helidon.messaging;

import java.util.Optional;

import io.helidon.service.registry.Service;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class ChannelRegistryTest {
    @Test
    void doesNotExposeAutoCloseableAsAServiceContract() {
        assertThat(AutoCloseable.class.isAssignableFrom(ChannelRegistry.class), is(false));
    }

    @Test
    void startsAtTheMessagingRunLevel() {
        assertThat(ChannelRegistry__ServiceDescriptor.INSTANCE.runLevel(), is(Optional.of(MessagingRuntime.RUN_LEVEL)));
    }

    @Test
    void lifecycleGuardStopsMessagingBeforeApplicationServices() {
        assertThat(MessagingLifecycleGuard__ServiceDescriptor.INSTANCE.runLevel(),
                   is(Optional.of(Service.RunLevel.NORMAL + 1)));
    }
}
