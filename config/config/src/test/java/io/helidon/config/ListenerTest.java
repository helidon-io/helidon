/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.config;

import io.helidon.common.reactive.Flow;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ListenerTest {

    /**
     * Compilation test.
     */
    @Test
    public void compilation() {
        Assertions.assertThrows(RuntimeException.class, () -> {
            Config config = null;
            config.get("my").get("app").get("security").changes().subscribe(new ConfigChangeSubscriber());
        });
    }

    class ConfigChangeSubscriber implements Flow.Subscriber<Config> {

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(Config event) {
            //react on change of appropriate ConfigSource
        }

        @Override
        public void onError(Throwable throwable) {
            //the ConfigSource is not more accessible...
        }

        @Override
        public void onComplete() {
        }
    }
}
