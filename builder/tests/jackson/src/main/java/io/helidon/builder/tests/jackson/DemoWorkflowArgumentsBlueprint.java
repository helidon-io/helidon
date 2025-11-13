package io.helidon.builder.tests.jackson;

import java.util.HashMap;
import java.util.Map;

import io.helidon.builder.api.Prototype;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@Prototype.Blueprint(beanStyle = true)
@Prototype.Extension(JsonSerialize.class) // annotations on implementation
@Prototype.Extension(JsonDeserialize.class) // annotations on builder and on prototype class
interface DemoWorkflowArgumentsBlueprint extends Loggable {
    @Override
    @JsonIgnore
    default Map<String, String> getDecorations() {
        HashMap<String, String> decorations = new HashMap<>();
        decorations.put(DefaultLogAndMetricsDecorator.CORRELATION_ID, "DemoWorkflowCorrelationId");
        return decorations;
    }

    @JsonProperty
    long getSize();

    @JsonProperty
    boolean isEncrypted();


    @JsonProperty
    int workflowId();

    /*
    @JsonDeserialize(builder = DemoWorkflowArguments.Builder.class) on prototype
    @JsonPOJOBuilder(withPrefix = "") on builder
    builder setters have @JsonProperty (and all deserialization annotations)
     */
}
