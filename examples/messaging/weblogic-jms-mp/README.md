# Helidon Messaging with Oracle Weblogic Example

## Prerequisites
* JDK 21+
* Maven
* Docker
* Account at https://container-registry.oracle.com/ with accepted Oracle Standard Terms and Restrictions for Weblogic.

## Run Weblogic in docker
1. You will need to do a docker login to Oracle container registry with account which previously
   accepted Oracle Standard Terms and Restrictions for Weblogic: 
   `docker login container-registry.oracle.com`
2. Run `bash buildAndRunWeblogic.sh` to build and run example Weblogic container.
   * After example JMS resources are deployed, Weblogic console should be available at http://localhost:7001/console with `admin`/`Welcome1`
3. To obtain wlthint3client.jar necessary for connecting to Weblogic execute 
   `bash extractThinClientLib.sh`, file will be copied to `./weblogic` folder.

## Build & Run
To run Helidon with thin client, flag `--add-opens=java.base/java.io=ALL-UNNAMED` is needed to
open java.base module to thin client internals.
```shell
#1.
 mvn clean package
#2.
 java --add-opens=java.base/java.io=ALL-UNNAMED -jar ./target/weblogic-jms-mp.jar
```
3. Visit http://localhost:8080 and try to send and receive messages over Weblogic JMS queue.

