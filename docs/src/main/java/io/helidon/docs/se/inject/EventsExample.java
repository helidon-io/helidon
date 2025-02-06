package io.helidon.docs.se.inject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionStage;

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
    record MyIdProducer(@Service.Named("id") Event.Emitter<String> emitter) {

        void emit(String msg) {
            emitter.emit(msg);
        }
    }
    // end::snippet_6[]

    // tag::snippet_7[]
    @Service.Singleton
    class MyIdObserver {

        @Event.Observer
        @Service.Named("id")
        void event(MyEvent event) {
            //Do something with the event
        }
    }
    // end::snippet_7[]

}
