package io.helidon.service.test.registry;

import io.helidon.service.registry.Service;
import io.helidon.service.registry.Services;
import io.helidon.testing.junit5.Testing;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@Testing.Test
class TestServicesSet {

    @Test
    void testServicesSet() {
        Services.set(Child.class, new ChildImpl("custom"));

        assertThat(Services.get(Child.class).message(), is("custom"));
        assertThat(Services.get(TopLevel.class).message(), is("custom"));

    }

    interface TopLevel {
        String message();
    }

    interface Child extends TopLevel {
    }

    @Service.Singleton
    static class ChildImpl implements Child {
        private final String message;

        @Service.Inject
        ChildImpl() {
            this.message = "injected";
        }

        ChildImpl(String message) {
            this.message = message;
        }

        @Override
        public String message() {
            return message;
        }
    }
}
