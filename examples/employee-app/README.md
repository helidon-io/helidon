# Helidon Quickstart SE - Employee Directory Example

This project implements an employee directory REST service using Helidon SE.
 The application is composed of a Helidon REST Microservice backend along with
 an HTML/JavaScript front end. The source for both application is included with
 the Maven project.

By default the service uses a ArrayList backend with sample data. You can connect
 the backend application to an Oracle database by changing the values in the
 `resources/application.yaml` file.

The service uses Helidon DB Client that provides reactive and non-blocking access to a database.

## Build and run

```bash
mvn package
java -jar target/employee-app.jar
```

## Create script
If you do not have the employee table in your database, you can create it and required resources as follows:
                
```sql
CREATE TABLE EMPLOYEE (ID NUMBER(6), 
    FIRSTNAME VARCHAR2(20), 
    LASTNAME VARCHAR2(25), 
    EMAIL VARCHAR2(30), 
    PHONE VARCHAR2(30),
    BIRTHDATE VARCHAR2(15),
    TITLE VARCHAR2(20),
    DEPARTMENT VARCHAR2(20));

ALTER TABLE EMPLOYEE ADD (CONSTRAINT emp_id_pk PRIMARY KEY (ID));

CREATE SEQUENCE EMPLOYEE_SEQ INCREMENT BY 1 NOCACHE NOCYCLE;
```

## Exercise the application
Get all employees.
```sh
curl -X GET curl -X GET http://localhost:8080/employees
```

Only 1 output record is shown for brevity:
```json
[
  {
    "birthDate": "1970-11-28T08:28:48.078Z",
    "department": "Mobility",
    "email": "Hugh.Jast@example.com",
    "firstName": "Hugh",
    "id": "48cf06ad-6ed4-47e6-ac44-3ea9c67cbe2d",
    "lastName": "Jast",
    "phone": "730-715-4446",
    "title": "National Data Strategist"
  }
]
```


Get all employees whose last name contains "S".
```sh
curl -X GET http://localhost:8080/employees/lastname/S
```

Only 1 output record is shown for brevity:
```json
[
  {
    "birthDate": "1978-03-18T17:00:12.938Z",
    "department": "Security",
    "email": "Zora.Sawayn@example.com",
    "firstName": "Zora",
    "id": "d7b583a2-f068-40d9-aec0-6f87899c5d8a",
    "lastName": "Sawayn",
    "phone": "923-814-0502",
    "title": "Dynamic Marketing Designer"
  }
]
```

Get an individual record.
```sh
curl -X GET http://localhost:8080/employees/48cf06ad-6ed4-47e6-ac44-3ea9c67cbe2d
```
Output:
```json
[
  {
    "birthDate": "1970-11-28T08:28:48.078Z",
    "department": "Mobility",
    "email": "Hugh.Jast@example.com",
    "firstName": "Hugh",
    "id": "48cf06ad-6ed4-47e6-ac44-3ea9c67cbe2d",
    "lastName": "Jast",
    "phone": "730-715-4446",
    "title": "National Data Strategist"
  }
]
```

Connect with a web brower at:
```txt
http://localhost:8080/public/index.html
```


## Try health and metrics

```sh
curl -s -X GET http://localhost:8080/health
```

```json
{
  "outcome": "UP",
  "checks": [
    {
      "name": "deadlock",
      "state": "UP"
    },
    {
      "name": "diskSpace",
      "state": "UP",
      "data": {
        "free": "306.61 GB",
        "freeBytes": 329225338880,
        "percentFree": "65.84%",
        "total": "465.72 GB",
        "totalBytes": 500068036608
      }
    },
    {
      "name": "heapMemory",
      "state": "UP",
      "data": {
        "free": "239.35 MB",
        "freeBytes": 250980656,
        "max": "4.00 GB",
        "maxBytes": 4294967296,
        "percentFree": "99.59%",
        "total": "256.00 MB",
        "totalBytes": 268435456
      }
    }
  ]
}
```

### Prometheus Format

```sh
curl -s -X GET http://localhost:8080/metrics
```

Only 1 output item is shown for brevity:
```txt
# TYPE base:classloader_current_loaded_class_count counter
# HELP base:classloader_current_loaded_class_count Displays the number of classes that are currently loaded in the Java virtual machine.
base:classloader_current_loaded_class_count 3995
```

### JSON Format
```sh
curl -H 'Accept: application/json' -X GET http://localhost:8080/metrics
```

Output:
```json
{
  "base": {
    "classloader.currentLoadedClass.count": 4011,
    "classloader.totalLoadedClass.count": 4011,
    "classloader.totalUnloadedClass.count": 0,
    "cpu.availableProcessors": 8,
    "cpu.systemLoadAverage": 1.65283203125,
    "gc.G1 Old Generation.count": 0,
    "gc.G1 Old Generation.time": 0,
    "gc.G1 Young Generation.count": 2,
    "gc.G1 Young Generation.time": 8,
    "jvm.uptime": 478733,
    "memory.committedHeap": 268435456,
    "memory.maxHeap": 4294967296,
    "memory.usedHeap": 18874368,
    "thread.count": 11,
    "thread.daemon.count": 4,
    "thread.max.count": 11
  },
  "vendor": {
    "grpc.requests.count": 0,
    "grpc.requests.meter": {
      "count": 0,
      "meanRate": 0,
      "oneMinRate": 0,
      "fiveMinRate": 0,
      "fifteenMinRate": 0
    },
    "requests.count": 5,
    "requests.meter": {
      "count": 5,
      "meanRate": 0.01046407983617782,
      "oneMinRate": 0.0023897243038835964,
      "fiveMinRate": 0.003944597070306631,
      "fifteenMinRate": 0.0023808575122958794
    }
  }
}
```

## Build the Docker Image

```sh
docker build -t employee-app .
```

## Start the application with Docker

```sh
docker run --rm -p 8080:8080 employee-app:latest
```

Exercise the application as described above.

## Deploy the application to Kubernetes

```txt
kubectl cluster-info                # Verify which cluster
kubectl get pods                    # Verify connectivity to cluster
kubectl create -f app.yaml   # Deply application
kubectl get service employee-app  # Get service info
```


###  Oracle DB Credentials
You can connect to two different datastores for the back end application.
 Just fill in the application.yaml files. To use an ArrayList as the data store,
 simply set `drivertype` to `Array`. To connect to an Oracle database, you must
 set all the values: `user`, `password`, `hosturl`, and `drivertype`.
 For Oracle, the `drivertype` should be set to `Oracle`.

**Sample `application.yaml`**
```yaml
app:
  user: <user-db>
  password: <password-user-db>
  hosturl: <hostname>:<port>/<database_unique_name>.<host_domain_name>
  drivertype: Array

  server:
    port: 8080
    host: 0.0.0.0
```

## Create the database objects

1. Create a connection to your Oracle Database using sqlplus or SQL Developer.
 See https://docs.cloud.oracle.com/iaas/Content/Database/Tasks/connectingDB.htm.
2. Create the database objects:

```sql
CREATE TABLE EMPLOYEE (
      ID INTEGER NOT NULL,
      FIRSTNAME VARCHAR(100),
      LASTNAME VARCHAR(100),
      EMAIL VARCHAR(100),
      PHONE VARCHAR(100),
      BIRTHDATE VARCHAR(10),
      TITLE VARCHAR(100),
      DEPARTMENT VARCHAR(100),
      PRIMARY KEY (ID)
      );
```

```sql
CREATE SEQUENCE EMPLOYEE_SEQ
      START WITH     100
      INCREMENT BY   1;
```

```sql
INSERT INTO EMPLOYEE (ID, FIRSTNAME, LASTNAME, EMAIL, PHONE, BIRTHDATE, TITLE, DEPARTMENT) VALUES (EMPLOYEE_SEQ.nextVal, 'Hugh', 'Jast', 'Hugh.Jast@example.com', '730-555-0100', '1970-11-28', 'National Data Strategist', 'Mobility');

INSERT INTO EMPLOYEE (ID, FIRSTNAME, LASTNAME, EMAIL, PHONE, BIRTHDATE, TITLE, DEPARTMENT) VALUES (EMPLOYEE_SEQ.nextVal, 'Toy', 'Herzog', 'Toy.Herzog@example.com', '769-555-0102', '1961-08-08', 'Dynamic Operations Manager', 'Paradigm');

INSERT INTO EMPLOYEE (ID, FIRSTNAME, LASTNAME, EMAIL, PHONE, BIRTHDATE, TITLE, DEPARTMENT) VALUES (EMPLOYEE_SEQ.nextVal, 'Reed', 'Hahn', 'Reed.Hahn@example.com', '429-555-0153', '1977-02-05', 'Future Directives Facilitator', 'Quality');

INSERT INTO EMPLOYEE (ID, FIRSTNAME, LASTNAME, EMAIL, PHONE, BIRTHDATE, TITLE, DEPARTMENT) VALUES (EMPLOYEE_SEQ.nextVal, 'Novella', 'Bahringer', 'Novella.Bahringer@example.com', '293-596-3547', '1961-07-25', 'Principal Factors Architect', 'Division');

INSERT INTO EMPLOYEE (ID, FIRSTNAME, LASTNAME, EMAIL, PHONE, BIRTHDATE, TITLE, DEPARTMENT) VALUES (EMPLOYEE_SEQ.nextVal, 'Zora', 'Sawayn', 'Zora.Sawayn@example.com', '923-555-0161', '1978-03-18', 'Dynamic Marketing Designer', 'Security');

INSERT INTO EMPLOYEE (ID, FIRSTNAME, LASTNAME, EMAIL, PHONE, BIRTHDATE, TITLE, DEPARTMENT) VALUES (EMPLOYEE_SEQ.nextVal, 'Cordia', 'Willms', 'Cordia.Willms@example.com', '778-555-0187', '1989-03-31', 'Human Division Representative', 'Optimization');
```
