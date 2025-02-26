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
package io.helidon.docs.se.inject;

import java.util.concurrent.CompletionStage;

import io.helidon.docs.se.inject.Qualifier2Example.Blue;
import io.helidon.service.registry.Event;
import io.helidon.service.registry.Service;

class EventsExample {

    // tag::snippet_1[]
    /**
     * A custom event payload.
     * @param msg message
     */
    record MyEvent(String msg) {
    }
    // end::snippet_1[]

    // tag::snippet_2[]
    @Service.Singleton
    record MyEventProducer(Event.Emitter<MyEvent> emitter) {

        void emit(String msg) {
            emitter.emit(new MyEvent(msg));
        }
    }
    // end::snippet_2[]


    // tag::snippet_3[]
    @Service.Singleton
    class MyEventObserver {

        @Event.Observer
        void event(MyEvent event) {
            //Do something with the event
        }
    }
    // end::snippet_3[]


    // tag::snippet_4[]
    @Service.Singleton
    record MyAsyncEmitter(Event.Emitter<MyEvent> emitter) {

        void emit(String msg) {
            CompletionStage<MyEvent> completionStage = emitter.emitAsync(new MyEvent(msg));
            //Do something with the completion stage
        }
    }
    // end::snippet_4[]

    // tag::snippet_5[]
    @Service.Singleton
    class MyEventAsyncObserver {

        @Event.AsyncObserver
        void event(MyEvent event) {
            //Do something with the event
        }
    }
    // end::snippet_5[]

    // tag::snippet_6[]
    @Service.Singleton
    record MyBlueProducer(@Blue Event.Emitter<String> emitter) {

        void emit(String msg) {
            emitter.emit(msg);
        }
    }
    // end::snippet_6[]

    // tag::snippet_7[]
    @Service.Singleton
    class MyBlueObserver {

        @Event.Observer
        @Blue
        void event(MyEvent event) {
            //Do something with the event
        }
    }
    // end::snippet_7[]

}
