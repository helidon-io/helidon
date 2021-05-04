/*
 * Copyright (c) 2019, 2021 Oracle and/or its affiliates.
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
import java.util.Objects;
import java.util.TreeMap;

import io.helidon.grpc.server.GrpcService;
import io.helidon.grpc.server.ServiceDescriptor;

import io.grpc.stub.StreamObserver;

import static io.helidon.grpc.core.ResponseHelper.complete;

/**
 * A simple class that implements gRPC service. Used for testing.
 */
public class TreeMapService
        implements GrpcService {

    /**
     * A reference to a {@link services.TreeMapService.Person} named "Bilbo".
     */
    public static Person BILBO = new Person(1, "Bilbo", 111, "Male", new String[] {"Burglaring", "Pipe smoking"});

    /**
     * A reference to a {@link services.TreeMapService.Person} named "Frodo".
     */
    public static Person FRODO = new Person(2, "Frodo", 33, "Male", new String[] {"Long hikes"});

    /**
     * A reference to a {@link services.TreeMapService.Person} named "Aragon".
     */
    public static Person ARAGON = new Person(3, "Aragon", 87, "Male", new String[] {"Pipe smoking", "Hitting on elvish women"});

    /**
     * A reference to a {@link services.TreeMapService.Person} named "Galadriel".
     */
    public static Person GALARDRIEL = new Person(4, "Galadriel", 8372, "Female", new String[] {"Dwarves"});

    /**
     * A reference to a {@link services.TreeMapService.Person} named "Gandalf".
     */
    public static Person GANDALF = new Person(5, "Gandalf", 32767, "Male", new String[] {"Wizardry"});

    private TreeMap<Integer, Person> lorMap = new TreeMap<>();

    public TreeMapService() {
        lorMap.put(1, BILBO);
        lorMap.put(2, FRODO);
        lorMap.put(3, ARAGON);
        lorMap.put(4, GALARDRIEL);
        lorMap.put(5, GANDALF);
    }

    @Override
    public void update(ServiceDescriptor.Rules config) {
        config.unary("get", this::get);
        config.serverStreaming("greaterOrEqualTo", this::greaterOrEqualTo);
        config.clientStreaming("sumOfAges", this::sumOfAges);
        config.bidirectional("persons", this::persons);
    }

    /**
     * Retrieve the person from the TreeMap. This is a UNARY call.
     *
     * @param id       The id of the person.
     * @param observer the call response
     */
    public void get(Integer id, StreamObserver<Person> observer) {
        complete(observer, lorMap.get(id));
    }

    /**
     * Return the Persons whose Ids are greater than or equal to the specified key. This is a ServerStreaming call.
     *
     * @param id       The id to use.
     * @param observer A {@link io.grpc.stub.StreamObserver} into which {@link services.TreeMapService.Person}s whose ids
     *                 are greater than or equal to the specified id will be emitted.
     */
    public void greaterOrEqualTo(Integer id, StreamObserver<Person> observer) {
        for (Person p : lorMap.tailMap(id).values()) {
            observer.onNext(p);
        }
        observer.onCompleted();
    }

    /**
     * Return the sum of ages of all Persons whose Ids are streamed  from the client. This is a Client streaming call.
     *
     * @param observer A {@link io.grpc.stub.StreamObserver} into which the sum of ages of
     *                 all {@link services.TreeMapService.Person}s will be emitted.
     * @return A {@link io.grpc.stub.StreamObserver} into which the ids of {@link services.TreeMapService.Person}s
     *         should be emitted into.
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

    /**
     * Streams the {@link services.TreeMapService.Person} into the specified observer for each of the id that is
     * streamed (from the client). This is a bi-directional streaming call.
     * @param observer A {@link io.grpc.stub.StreamObserver} into which the sum of ages of
     *                 all {@link services.TreeMapService.Person}s will be emitted.
     * @return A {@link io.grpc.stub.StreamObserver} into which the ids of {@link services.TreeMapService.Person}s
     * should be emitted into.
     */
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

    /**
     * A person class used in the test code.
     */
    public static class Person
            implements Serializable {
        private int id;
        private String name;
        private int age;
        private String gender;
        private String[] hobbies;

        /**
         * Creates a new Person.
         * @param id The id of the person.
         * @param name The name of the person.
         * @param age The age of the person.
         * @param gender The gender of the person.
         * @param hobbies The hobbies of the person.
         */
        public Person(int id, String name, int age, String gender, String[] hobbies) {
            this.id = id;
            this.name = name;
            this.age = age;
            this.gender = gender;
            this.hobbies = hobbies;
        }

        /**
         * Returns the Id of the person.
         * @return The id of the person.
         */
        public int getId() {
            return id;
        }

        /**
         * Returns the name of the person.
         * @return The name of the person.
         */
        public String getName() {
            return name;
        }

        /**
         * Returns the gender of the person.
         * @return The gender of the person.
         */
        public String getGender() {
            return gender;
        }

        /**
         * Returns the hobbies of the person.
         * @return The hobbies of the person.
         */
        public String[] getHobbies() {
            return hobbies;
        }

        /**
         * Returns the age of the person.
         * @return The age of the person.
         */
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
            if (!(o instanceof Person)) {
                return false;
            }
            Person person = (Person) o;
            return getId() == person.getId() &&
                    getAge() == person.getAge() &&
                    Objects.equals(getName(), person.getName()) &&
                    Objects.equals(getGender(), person.getGender()) &&
                    Arrays.equals(getHobbies(), person.getHobbies());
        }

        @Override
        public int hashCode() {
            int result = Objects.hash(getId(), getName(), getAge(), getGender());
            result = 31 * result + Arrays.hashCode(getHobbies());
            return result;
        }
    }
}
