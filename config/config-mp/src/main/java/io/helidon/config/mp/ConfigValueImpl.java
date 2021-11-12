package io.helidon.config.mp;

import org.eclipse.microprofile.config.ConfigValue;

record ConfigValueImpl(String name,
                       String value,
                       String rawValue,
                       String sourceName,
                       int sourceOrdinal) implements ConfigValue {

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getValue() {
        return value;
    }

    @Override
    public String getRawValue() {
        return rawValue;
    }

    @Override
    public String getSourceName() {
        return sourceName;
    }

    @Override
    public int getSourceOrdinal() {
        return sourceOrdinal;
    }
}
