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
package io.helidon.webserver.tests.grpc;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.grpc.stub.StreamObserver;

class TestObserver<T> implements StreamObserver<T> {

    private CountDownLatch latch;
    private final List<T> responses = new ArrayList<>();

    TestObserver() {
    }

    TestObserver(int count) {
        latch(count);
    }

    public CountDownLatch latch(int count) {
        latch = new CountDownLatch(count);
        return latch;
    }

    public List<T> getResponses() {
        return responses;
    }

    public void clear() {
        responses.clear();
    }

    @Override
    public void onNext(T response) {
        responses.add(response);
        if (latch != null) {
            latch.countDown();
        }
    }

    @Override
    public void onError(Throwable throwable) {
    }

    @Override
    public void onCompleted() {
    }

    public boolean await(long timeout, TimeUnit unit) throws InterruptedException {
        if (latch != null) {
            return latch.await(timeout, unit);
        }
        return false;
    }
}
