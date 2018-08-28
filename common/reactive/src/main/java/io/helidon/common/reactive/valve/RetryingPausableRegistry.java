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

package io.helidon.common.reactive.valve;

import java.util.function.BiConsumer;

/**
 * The RetryingPausableRegistry.
 */
abstract class RetryingPausableRegistry<T> extends PausableRegistry<T> {
    @Override
    protected void tryProcess() {
        if (canProcess()) {
            try {
                BiConsumer<T, Pausable> onData = getOnData();
                boolean breakByPause = false;
                T data;
                while ((data = moreData()) != null) {
                    onData.accept(data, this);
                    if (!canContinueProcessing()) {
                        breakByPause = true;
                        break;
                    }
                }
                if (!breakByPause && getOnComplete() != null) {
                    getOnComplete().run();
                }
            } catch (Throwable thr) {
                handleError(thr);
                releaseProcessing();
            }
        }
    }

    protected abstract T moreData() throws Throwable;
}
