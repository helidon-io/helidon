package io.helidon.webclient.context;

import java.util.Optional;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.http.HeaderName;

@Prototype.Configured
@Prototype.CustomMethods(WebClientContextConfigSupport.RecordCustomMethods.class)
interface ContextRecordBlueprint {
    /**
     * Name of the header to use when sending the context value over the network.
     *
     * @return header name
     */
    @Option.Configured
    HeaderName header();

    /**
     * String classifier of the value that will be used with {@link io.helidon.common.context.Context#get(Object, Class)}.
     *
     * @return classifier to use, defaults to header name
     */
    @Option.Configured
    Optional<String> classifier();

    /**
     * Default value to send if not configured in context.
     *
     * @return default value
     */
    @Option.Configured
    Optional<String> defaultValue();

    /**
     * Whether to treat the option as an array of strings.
     * This would be read from the context as an array.
     *
     * @return whether the record is an array
     */
    @Option.Configured
    boolean array();
}
