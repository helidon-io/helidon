/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package services;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Stream;

import io.helidon.grpc.client.test.Echo;
import io.helidon.grpc.server.GrpcService;
import io.helidon.grpc.server.ServiceDescriptor;

import io.grpc.stub.StreamObserver;

/**
 * A simple class that implements gRPC service. USed for testing.
 */
public class TreeMapService
        implements GrpcService {

    private TreeMap<Integer, Person> lorMap = new TreeMap<>();

    public static Person BILBO = new Person(1, "Bilbo", 111, "Male", new String[] {"Burglaring", "Pipe smoking"});
    public static Person FRODO = new Person(2, "Frodo", 33, "Male", new String[] {"Long hikes"});
    public static Person ARAGON = new Person(3, "Aragon", 87, "Male", new String[] {"Pipe smoking", "Hitting on elvish women"});
    public static Person GALARDRIAL = new Person(4, "Galadriel", 8372, "Female", new String[] {"Dwarves"});
    public static Person GANDALF = new Person(5, "Gandalf", 32767, "Male", new String[] {"Wizardry"});

    public TreeMapService() {
        lorMap.put(1, BILBO);
        lorMap.put(2, ARAGON);
        lorMap.put(3, ARAGON);
        lorMap.put(4, GALARDRIAL);
        lorMap.put(5, GANDALF);
    }

    @Override
    public void update(ServiceDescriptor.Config config) {
        config.unary("get", this::get);
        config.serverStreaming("greaterOrEqualTo", this::greaterOrEqualTo);
        config.clientStreaming("sumOfAges", this::sumOfAges);
        config.bidirectional("persons", this::persons);
    }


    /**
     * Retrieve the person from the TreeMap.
     *
     * @param id The id of the person.
     * @param observer  the call response
     */
    public void get(Integer id, StreamObserver<Person> observer) {
        complete(observer, lorMap.get(id));
    }

    /**
     * Store the person in the TreeMap.
     *
     * @param req The PutRequest
     * @param observer  the call response
     */
    public void put(PutRequest req, StreamObserver<Person> observer) {
        complete(observer, lorMap.put(req.getPerson().getId(), req.getPerson()));
    }

    /**
     * ServerStreaming call. Return the Persons whose Ids are greater than or equal to the specified key.
     * @param id The id to use.
     * @param observer
     */
    public void greaterOrEqualTo(Integer id, StreamObserver<Person> observer) {
        for (Person p : lorMap.tailMap(id).values()) {
            observer.onNext(p);
        }
        observer.onCompleted();
    }

    /**
     * Client streaming call. Return the sum of ages of all Persons whose Ids are streamed  from the client.
     * @param observer The ids to find the sum of ages.
     * @param observer
     */
    public StreamObserver<Integer> sumOfAges(StreamObserver<Integer> observer) {
        return new StreamObserver<Integer>() {
            private int sum = 0;

            public void onNext(Integer id) {
                System.out.println("Received id: ==> " + id);
                Person p = lorMap.get(id);
                sum += p != null ? p.age : 0;
            }

            public void onError(Throwable t) {
                t.printStackTrace();
            }

            public void onCompleted() {
                observer.onNext(sum);
                observer.onCompleted();
            }
        };
    }

    public StreamObserver<Integer> persons(StreamObserver<Person> observer) {
        return new StreamObserver<Integer>() {
            public void onNext(Integer id) {
                Person p = lorMap.get(id);
                if (p != null) {
                    observer.onNext(p);
                }
            }

            public void onError(Throwable t) {
                t.printStackTrace();
            }

            public void onCompleted() {
                observer.onCompleted();
            }
        };
    }

    public static class PutRequest
        implements Serializable {

        private Person person;

        public PutRequest(Person person) {
            this.person = person;
        }

        public Person getPerson() {
            return person;
        }
    }

    public static class Person
            implements Serializable {
        private int id;
        private String name;
        private int age;
        private String gender;
        private String[] hobbies = new String[0];

        public int getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public String getGender() {
            return gender;
        }

        public String[] getHobbies() {
            return hobbies;
        }

        public Person(int id, String name, int age, String gender, String[] hobbies) {
            this.id = id;
            this.name = name;
            this.age = age;
            this.gender = gender;
            this.hobbies = hobbies;
        }

        public int getAge() {
            return age;
        }

        @Override
        public String toString() {
            return "Person{" +
                    "id=" + id +
                    ", name='" + name + '\'' +
                    ", age=" + age +
                    ", gender='" + gender + '\'' +
                    ", hobbies=" + Arrays.toString(hobbies) +
                    '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            Person person = (Person) o;
            return id == person.id &&
                    age == person.age &&
                    Objects.equals(name, person.name) &&
                    Objects.equals(gender, person.gender) &&
                    Arrays.equals(hobbies, person.hobbies);
        }

        @Override
        public int hashCode() {
            int result = Objects.hash(id, name, age, gender);
            result = 31 * result + Arrays.hashCode(hobbies);
            return result;
        }
    }
}
