package io.helidon.lra;

public class KafkaChannelConfig {
    String bootstrapservers, sendtotopic, groupid;

    public String toString() {
        return "bootstrapservers:" + bootstrapservers + " topic:" + sendtotopic + " groupid:" + groupid ;
    }
}
