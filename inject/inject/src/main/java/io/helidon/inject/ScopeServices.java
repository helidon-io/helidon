package io.helidon.inject;

public interface ScopeServices extends Services {
    void bind(Activator<?> acctivator);
    void close();
}
