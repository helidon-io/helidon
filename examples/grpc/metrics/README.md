# Helidon gRPC Metrics Example

A basic example using metrics with gRPC server.

## Build and run

```shell
mvn -f ../pom.xml -pl common,metrics package
java -jar target/helidon-examples-grpc-metrics.jar
```

Run the GreetService client:
```shell
java -cp target/helidon-examples-grpc-metrics.jar io.helidon.grpc.examples.common.GreetClient
```

Run the StringService client:
```shell
java -cp target/helidon-examples-grpc-metrics.jar io.helidon.grpc.examples.common.StringClient
```

Retrieve the metrics:
```shell
curl http://localhost:8080/metrics
```

Notice that you will get application metrics from the Helidon server metric response similar to this:
```text
...
# TYPE application_GreetService_Greet_total counter
# HELP application_GreetService_Greet_total 
application_GreetService_Greet_total 2
# TYPE application_GreetService_SetGreeting_total counter
# HELP application_GreetService_SetGreeting_total 
application_GreetService_SetGreeting_total 1
# TYPE application_StringService_Echo_rate_per_second gauge
application_StringService_Echo_rate_per_second 0.12718615252125942
# TYPE application_StringService_Echo_one_min_rate_per_second gauge
application_StringService_Echo_one_min_rate_per_second 0.2
# TYPE application_StringService_Echo_five_min_rate_per_second gauge
application_StringService_Echo_five_min_rate_per_second 0.2
# TYPE application_StringService_Echo_fifteen_min_rate_per_second gauge
application_StringService_Echo_fifteen_min_rate_per_second 0.2
# TYPE application_StringService_Echo_mean_seconds gauge
application_StringService_Echo_mean_seconds 0.001451683
# TYPE application_StringService_Echo_max_seconds gauge
application_StringService_Echo_max_seconds 0.001451683
# TYPE application_StringService_Echo_min_seconds gauge
application_StringService_Echo_min_seconds 0.001451683
# TYPE application_StringService_Echo_stddev_seconds gauge
application_StringService_Echo_stddev_seconds 0.0
# TYPE application_StringService_Echo_seconds summary
# HELP application_StringService_Echo_seconds 
application_StringService_Echo_seconds_count 1
application_StringService_Echo_seconds_sum 0
application_StringService_Echo_seconds{quantile="0.5"} 0.001451683
application_StringService_Echo_seconds{quantile="0.75"} 0.001451683
application_StringService_Echo_seconds{quantile="0.95"} 0.001451683
application_StringService_Echo_seconds{quantile="0.98"} 0.001451683
application_StringService_Echo_seconds{quantile="0.99"} 0.001451683
application_StringService_Echo_seconds{quantile="0.999"} 0.001451683
# TYPE application_StringService_Join_rate_per_second gauge
application_StringService_Join_rate_per_second 0.25353058349417795
# TYPE application_StringService_Join_one_min_rate_per_second gauge
application_StringService_Join_one_min_rate_per_second 0.4
# TYPE application_StringService_Join_five_min_rate_per_second gauge
application_StringService_Join_five_min_rate_per_second 0.4
# TYPE application_StringService_Join_fifteen_min_rate_per_second gauge
application_StringService_Join_fifteen_min_rate_per_second 0.4
# TYPE application_StringService_Join_mean_seconds gauge
application_StringService_Join_mean_seconds 0.002281452
# TYPE application_StringService_Join_max_seconds gauge
application_StringService_Join_max_seconds 0.003709154
# TYPE application_StringService_Join_min_seconds gauge
application_StringService_Join_min_seconds 8.5375E-4
# TYPE application_StringService_Join_stddev_seconds gauge
application_StringService_Join_stddev_seconds 0.001427702
# TYPE application_StringService_Join_seconds summary
# HELP application_StringService_Join_seconds 
application_StringService_Join_seconds_count 2
application_StringService_Join_seconds_sum 0
application_StringService_Join_seconds{quantile="0.5"} 0.003709154
application_StringService_Join_seconds{quantile="0.75"} 0.003709154
application_StringService_Join_seconds{quantile="0.95"} 0.003709154
application_StringService_Join_seconds{quantile="0.98"} 0.003709154
application_StringService_Join_seconds{quantile="0.99"} 0.003709154
application_StringService_Join_seconds{quantile="0.999"} 0.003709154
# TYPE application_StringService_Lower_rate_per_second gauge
application_StringService_Lower_rate_per_second 0.25274282459020236
# TYPE application_StringService_Lower_one_min_rate_per_second gauge
application_StringService_Lower_one_min_rate_per_second 0.4
# TYPE application_StringService_Lower_five_min_rate_per_second gauge
application_StringService_Lower_five_min_rate_per_second 0.4
# TYPE application_StringService_Lower_fifteen_min_rate_per_second gauge
application_StringService_Lower_fifteen_min_rate_per_second 0.4
# TYPE application_StringService_Lower_mean_seconds gauge
application_StringService_Lower_mean_seconds 5.606925E-4
# TYPE application_StringService_Lower_max_seconds gauge
application_StringService_Lower_max_seconds 7.27866E-4
# TYPE application_StringService_Lower_min_seconds gauge
application_StringService_Lower_min_seconds 3.93519E-4
# TYPE application_StringService_Lower_stddev_seconds gauge
application_StringService_Lower_stddev_seconds 1.671735E-4
# TYPE application_StringService_Lower_seconds summary
# HELP application_StringService_Lower_seconds 
application_StringService_Lower_seconds_count 2
application_StringService_Lower_seconds_sum 0
application_StringService_Lower_seconds{quantile="0.5"} 7.27866E-4
application_StringService_Lower_seconds{quantile="0.75"} 7.27866E-4
application_StringService_Lower_seconds{quantile="0.95"} 7.27866E-4
application_StringService_Lower_seconds{quantile="0.98"} 7.27866E-4
application_StringService_Lower_seconds{quantile="0.99"} 7.27866E-4
application_StringService_Lower_seconds{quantile="0.999"} 7.27866E-4
# TYPE application_StringService_Split_rate_per_second gauge
application_StringService_Split_rate_per_second 0.25378693218040604
# TYPE application_StringService_Split_one_min_rate_per_second gauge
application_StringService_Split_one_min_rate_per_second 0.4
# TYPE application_StringService_Split_five_min_rate_per_second gauge
application_StringService_Split_five_min_rate_per_second 0.4
# TYPE application_StringService_Split_fifteen_min_rate_per_second gauge
application_StringService_Split_fifteen_min_rate_per_second 0.4
# TYPE application_StringService_Split_mean_seconds gauge
application_StringService_Split_mean_seconds 9.63112E-4
# TYPE application_StringService_Split_max_seconds gauge
application_StringService_Split_max_seconds 0.001190785
# TYPE application_StringService_Split_min_seconds gauge
application_StringService_Split_min_seconds 7.35439E-4
# TYPE application_StringService_Split_stddev_seconds gauge
application_StringService_Split_stddev_seconds 2.27673E-4
# TYPE application_StringService_Split_seconds summary
# HELP application_StringService_Split_seconds 
application_StringService_Split_seconds_count 2
application_StringService_Split_seconds_sum 0
application_StringService_Split_seconds{quantile="0.5"} 0.001190785
application_StringService_Split_seconds{quantile="0.75"} 0.001190785
application_StringService_Split_seconds{quantile="0.95"} 0.001190785
application_StringService_Split_seconds{quantile="0.98"} 0.001190785
application_StringService_Split_seconds{quantile="0.99"} 0.001190785
application_StringService_Split_seconds{quantile="0.999"} 0.001190785
# TYPE application_StringService_Upper_mean gauge
application_StringService_Upper_mean 1
# TYPE application_StringService_Upper_max gauge
application_StringService_Upper_max 1
# TYPE application_StringService_Upper_min gauge
application_StringService_Upper_min 1
# TYPE application_StringService_Upper_stddev gauge
application_StringService_Upper_stddev 0
# TYPE application_StringService_Upper summary
# HELP application_StringService_Upper 
application_StringService_Upper_count 1
application_StringService_Upper_sum 1
application_StringService_Upper{quantile="0.5"} 1
application_StringService_Upper{quantile="0.75"} 1
application_StringService_Upper{quantile="0.95"} 1
application_StringService_Upper{quantile="0.98"} 1
application_StringService_Upper{quantile="0.99"} 1
application_StringService_Upper{quantile="0.999"} 1
...
```
