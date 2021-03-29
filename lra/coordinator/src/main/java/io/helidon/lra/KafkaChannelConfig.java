package io.helidon.lra;

public class KafkaChannelConfig {
    String bootstrapservers, sendtotopic, replytopic, groupid;

    public String toString() {
        return "bootstrapservers:" + bootstrapservers + " sendtotopic:" + sendtotopic + " replytopic:" + replytopic + " groupid:" + groupid ;
    }
}
