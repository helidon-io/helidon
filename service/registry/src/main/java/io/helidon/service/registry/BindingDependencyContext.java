package io.helidon.service.registry;

class BindingDependencyContext implements DependencyContext {
    private final Bindings.ServiceBindingPlan serviceBinding;

    BindingDependencyContext(Bindings.ServiceBindingPlan serviceBinding) {
        this.serviceBinding = serviceBinding;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T dependency(Dependency dependency) {
        Bindings.DependencyBinding binding = serviceBinding.binding(dependency);
        // services that match
        return (T) binding.instanceSupply().get();
    }
}
