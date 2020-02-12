# Docker build with local SNAPSHOT

## Start Nexus

```bash
docker volume create --name nexus-data
docker run -d -p 8081:8081 --name nexus -v nexus-data:/nexus-data sonatype/nexus3
```

Nexus can take some time to start, ping the following URL to check the status:
 `http://localhost:8081`

## Configure nexus credentials

```xml
<settings>
    <servers>
        <server>
            <id>local-nexus</id>
            <username>admin</username>
            <password>admin123</password>
        </server>
    </servers>
</settings>
```

## Deploy SNAPSHOT artifacts to Nexus

```bash
mvn deploy -DskipTests \
  -DaltDeploymentRepository=local-nexus::default::http://localhost:8081/repository/maven-snapshots
```

## Update the Dockerfile

Create a `settings.xml` as follow:

```xml
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0
                      http://maven.apache.org/xsd/settings-1.0.0.xsd">

    <localRepository>/tmp/repository</localRepository>
    <profiles>
        <!--
            A profile that configure an additional repository and pluginRepository
            when X_REPO is set in the environment.
        -->
        <profile>
            <id>x-repo</id>
            <activation>
                <property>
                    <name>env.X_REPO</name>
                </property>
            </activation>
            <repositories>
                <repository>
                    <id>x-repo</id>
                    <url>${env.X_REPO}</url>
                    <snapshots>
                        <updatePolicy>never</updatePolicy>
                        <enabled>true</enabled>
                    </snapshots>
                    <releases>
                        <enabled>true</enabled>
                    </releases>
                </repository>
            </repositories>
            <pluginRepositories>
                <pluginRepository>
                    <id>x-repo</id>
                    <url>${env.X_REPO}</url>
                    <snapshots>
                        <updatePolicy>never</updatePolicy>
                        <enabled>true</enabled>
                    </snapshots>
                    <releases>
                        <enabled>true</enabled>
                    </releases>
                </pluginRepository>
            </pluginRepositories>
        </profile>
    </profiles>
</settings>
```

Update the `Dockerfile` and add the following lines:

```
# A build time argument that can be used to configure an extra Maven repository.
# E.g. --build-arg X_REPO=my-repo-url
ARG X_REPO
ADD settings.xml /usr/share/maven/conf/
```

## Build your image

Replace `DOCKER_HOST` with a host name or IP address that can be used to reach
 the nexus server from inside a container. (E.g. `docker.for.mac.localhost`).

```bash
docker build \
  --build-arg X_REPO=http://DOCKER_HOST:8081/repository/maven-public \
  -t my-image-tag \
  .
```
