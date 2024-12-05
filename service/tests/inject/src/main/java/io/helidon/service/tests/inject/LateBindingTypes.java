package io.helidon.service.tests.inject;

import io.helidon.service.registry.Service;

class LateBindingTypes {
    interface Contract {
        String message();
    }

    @Service.Singleton
    static class ServiceProvider implements Contract {
        private final String message;

        @Service.Inject
        ServiceProvider() {
            this.message = "injected";
        }

        ServiceProvider(String message) {
            this.message = message;
        }

        @Override
        public String message() {
            return message;
        }
    }
}
