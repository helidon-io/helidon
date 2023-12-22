package io.helidon.inject;

import io.helidon.common.types.TypeName;

public interface ScopeServices extends Services {
    void bind(Activator<?> activator);
    void close();

    TypeName scope();
}
