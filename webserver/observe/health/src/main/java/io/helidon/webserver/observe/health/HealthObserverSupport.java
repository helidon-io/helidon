package io.helidon.webserver.observe.health;

import io.helidon.builder.api.Prototype;
import io.helidon.health.HealthCheck;
import io.helidon.health.HealthCheckResponse;
import io.helidon.health.HealthCheckType;

final class HealthObserverSupport {
    private HealthObserverSupport() {
    }

    static final class CustomMethods {
        private CustomMethods() {
        }

        /**
         * Add the provided health check using an explicit type (may differ from the
         * {@link io.helidon.health.HealthCheck#type()}.
         *
         * @param builder required for the custom method
         * @param check   health check to add
         * @param type    type to use
         */
        @Prototype.BuilderMethod
        static void addCheck(HealthObserverConfig.BuilderBase<?, ?> builder, HealthCheck check, HealthCheckType type) {
            if (check.type() == type) {
                builder.addCheck(check);
            } else {
                builder.addCheck(new TypedCheck(check, type));
            }
        }
    }

    private static final class TypedCheck implements HealthCheck {
        private final HealthCheck delegate;
        private final HealthCheckType type;

        private TypedCheck(HealthCheck delegate, HealthCheckType type) {
            this.delegate = delegate;
            this.type = type;
        }

        @Override
        public HealthCheckType type() {
            return type;
        }

        @Override
        public String name() {
            return delegate.name();
        }

        @Override
        public String path() {
            return delegate.path();
        }

        @Override
        public HealthCheckResponse call() {
            return delegate.call();
        }
    }
}
