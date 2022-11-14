clear
echo "RUN 1: (DAGGER2)"
java -jar dagger2/target/pico-examples-car-dagger2-4.0.0-SNAPSHOT-jar-with-dependencies.jar dagger2
echo "RUN 2: (DAGGER2)"
java -jar dagger2/target/pico-examples-car-dagger2-4.0.0-SNAPSHOT-jar-with-dependencies.jar dagger2
echo "========================"
echo "RUN 1: (PICO))"
java -jar pico/target/pico-examples-car-pico-4.0.0-SNAPSHOT-jar-with-dependencies.jar pico
echo "RUN 2: (PICO))"
java -jar pico/target/pico-examples-car-pico-4.0.0-SNAPSHOT-jar-with-dependencies.jar pico
