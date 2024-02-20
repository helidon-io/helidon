package io.helidon.service.inject;

import io.helidon.common.types.TypeName;
import io.helidon.service.registry.Service;

/**
 * An Application instance, if available at runtime, will be expected to provide a plan for all service provider's injection
 * points.
 * <p>
 * Implementations of this contract are normally code generated, although then can be programmatically written by the developer
 * for special cases.
 */
@Service.Contract
public interface Application {
    /**
     * Type name of this interface.
     */
    TypeName TYPE = TypeName.create(Application.class);

    /**
     * Name of this application.
     *
     * @return application name
     */
    String name();

    /**
     * Configure injection points and dependencies in this application.
     *
     * @param binder the binder used to register the service provider injection plans
     */
    void configure(InjectionPlanBinder binder);
}
