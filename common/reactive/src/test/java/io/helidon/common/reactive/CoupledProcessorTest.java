/*
 * Copyright (c)  2020 Oracle and/or its affiliates. All rights reserved.
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
 *
 */

package io.helidon.common.reactive;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class CoupledProcessorTest {
    @Test
    void signalOnErrorTest() {
        Multi<Integer> firstStream = Multi.error(new Exception("Test"));
        Multi<Integer> secondStream = Multi.just(1, 2, 3);
        MultiTappedProcessor<Integer> passedInSubscriber = MultiTappedProcessor.create();
        MultiCoupledProcessor<Integer, Integer> coupledProcessor = MultiCoupledProcessor.create(passedInSubscriber, secondStream);
        Single<Integer> firstSingle = Multi.from(coupledProcessor).first();
        Single<Integer> secondSingle = Multi.from(passedInSubscriber).first();
        firstStream.subscribe(coupledProcessor);

        Assertions.assertThrows(Exception.class, () -> firstSingle.get(1, TimeUnit.SECONDS));
        Assertions.assertThrows(Exception.class, () -> secondSingle.get(1, TimeUnit.SECONDS));
    }

    @Test
    void signalOnCompleteTest() throws InterruptedException, ExecutionException, TimeoutException {
        Multi<Integer> firstStream = Multi.just(4, 5, 6);
        Multi<Integer> secondStream = Multi.just(1, 2, 3);
        MultiTappedProcessor<Integer> passedInSubscriber = MultiTappedProcessor.create();
        MultiCoupledProcessor<Integer, Integer> coupledProcessor = MultiCoupledProcessor.create(passedInSubscriber, secondStream);
        Single<List<Integer>> firstResult = Multi.from(coupledProcessor).collectList();
        Single<List<Integer>> secondResult = Multi.from(passedInSubscriber).collectList();
        firstStream.subscribe(coupledProcessor);

        Assertions.assertEquals(List.of(1, 2, 3), firstResult.get(1, TimeUnit.SECONDS));
        Assertions.assertEquals(List.of(), secondResult.get(1, TimeUnit.SECONDS), "Needs to be empty, complete signal already sent by upstream");
    }
}
