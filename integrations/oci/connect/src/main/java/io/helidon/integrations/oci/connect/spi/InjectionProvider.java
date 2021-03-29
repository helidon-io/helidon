package io.helidon.integrations.oci.connect.spi;

import java.lang.reflect.Type;
import java.util.Set;

import io.helidon.config.Config;
import io.helidon.integrations.oci.connect.OciRestApi;

public interface InjectionProvider<T> {

    Set<Type> types();

    T createInstance(OciRestApi restApi, Config ociConfig);
}
