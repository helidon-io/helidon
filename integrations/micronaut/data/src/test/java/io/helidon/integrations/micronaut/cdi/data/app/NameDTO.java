package io.helidon.integrations.micronaut.cdi.data.app;

import io.micronaut.core.annotation.Introspected;

@Introspected
public class NameDTO {
    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
