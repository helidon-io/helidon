clear
echo "RUN 1: (HK2)"
java -cp target/pico-examples-book-4.0.0-SNAPSHOT-jar-with-dependencies.jar io.helidon.pico.examples.book.MainHk2
echo "RUN 2: (HK2)"
java -cp target/pico-examples-book-4.0.0-SNAPSHOT-jar-with-dependencies.jar io.helidon.pico.examples.book.MainHk2
echo "========================"
echo "RUN 1: (PICO))"
java -cp target/pico-examples-book-4.0.0-SNAPSHOT-jar-with-dependencies.jar io.helidon.pico.examples.book.MainPico
echo "RUN 2: (PICO))"
java -cp target/pico-examples-book-4.0.0-SNAPSHOT-jar-with-dependencies.jar io.helidon.pico.examples.book.MainPico
