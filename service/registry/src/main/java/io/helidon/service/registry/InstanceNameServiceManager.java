package io.helidon.service.registry;

class InstanceNameServiceManager extends ServiceManager<String> {
    InstanceNameServiceManager(CoreServiceRegistry registry) {
        // this is never used - we replace it in dependency injection plan with the correct instance
        super(registry, () -> null, null, true, () -> null);
    }

    @Override
    void ensureBindingPlan() {
    }

    @Override
    ServiceInfo descriptor() {
        return InstanceName__ServiceDescriptor.INSTANCE;
    }
}
