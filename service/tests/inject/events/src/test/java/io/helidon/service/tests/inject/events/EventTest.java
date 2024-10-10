package io.helidon.service.tests.inject.events;

import io.helidon.service.inject.InjectRegistryManager;
import io.helidon.service.inject.api.InjectRegistry;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

class EventTest {
    private static InjectRegistryManager registryManager;
    private static InjectRegistry registry;

    @BeforeAll
    static void initRegistry() {
        registryManager = InjectRegistryManager.create();
        registry = registryManager.registry();
    }

    @AfterAll
    static void tearDownRegistry() {
        registryManager.shutdown();
    }

    @Test
    void testEvent() {
        EventEmitter eventProducer = registry.get(EventEmitter.class);
        eventProducer.emit(new EventObject("unit-test-event"));

        EventReceiver eventReceiver = registry.get(EventReceiver.class);
        EventObject eventObject = eventReceiver.eventObject();
        assertThat("Event should have been received", eventObject, notNullValue());
        assertThat(eventObject.message(), is("unit-test-event"));
    }
}
