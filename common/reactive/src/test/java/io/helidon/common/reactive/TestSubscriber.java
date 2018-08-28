/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.common.reactive;

import java.util.LinkedList;
import java.util.List;

/**
 * A dummy subscriber for testing purpose.
 */
public class TestSubscriber implements Flow.Subscriber<String> {

    private Flow.Subscription subcription = null;
    private final List<String> items = new LinkedList<>();
    private String lastItem = null;
    private Throwable lastError = null;
    private boolean complete = false;

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        this.subcription = subscription;
    }

    /**
     * Request one item.
     */
    public void request1() {
        this.subcription.request(1);
    }

    /**
     * Request the maximum number of items.
     */
    public void requestMax() {
        this.subcription.request(Long.MAX_VALUE);
    }

    @Override
    public void onNext(String item) {
        items.add(item);
        lastItem = item;
    }

    @Override
    public void onError(Throwable throwable) {
        this.lastError = throwable;
    }

    /**
     * Indicates completeness.
     * @return {@code true} if complete, {@code false} otherwise
     */
    public boolean isComplete() {
        return complete;
    }

    @Override
    public void onComplete() {
        this.complete = true;
    }

    /**
     * Get the items accumulated by this subscriber.
     * @return list of items
     */
    public List<String> getItems() {
        return items;
    }

    /**
     * Get the last item accumulated by this subscriber.
     * @return last item, or {@code null} or this subscriber has not
     * received any items yet
     */
    public String getLastItem() {
        return lastItem;
    }

    /**
     * Get the last error received by this subscriber.
     * @return a {@code Throwable} or {@code null} or this subscriber has not
     * received any
     */
    public Throwable getLastError() {
        return lastError;
    }

    /**
     * Get the subscription set on this subscriber.
     * @return a {@code Flow.Subscription} or {@code null} if onSubcribe has not
     * been called
     */
    public Flow.Subscription getSubcription() {
        return subcription;
    }
}
