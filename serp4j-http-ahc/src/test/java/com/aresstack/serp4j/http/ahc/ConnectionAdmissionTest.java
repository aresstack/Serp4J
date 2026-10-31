package com.aresstack.serp4j.http.ahc;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConnectionAdmissionTest {

    @Test
    void queuesOverTheLimitsAndAdmitsInOrderOnRelease() {
        ConnectionAdmission admission = new ConnectionAdmission(3, 2);
        List<String> started = new ArrayList<String>();

        admission.acquire("a", () -> started.add("a1"));
        admission.acquire("a", () -> started.add("a2"));
        admission.acquire("a", () -> started.add("a3"));
        admission.acquire("b", () -> started.add("b1"));
        admission.acquire("b", () -> started.add("b2"));

        assertThat(started).containsExactly("a1", "a2", "b1");
        assertThat(admission.inFlight()).isEqualTo(3);
        assertThat(admission.waiting()).isEqualTo(2);

        admission.release("b");
        assertThat(started).containsExactly("a1", "a2", "b1", "b2");

        admission.release("a");
        assertThat(started).containsExactly("a1", "a2", "b1", "b2", "a3");
        assertThat(admission.waiting()).isZero();
    }

    @Test
    void anOriginWithFreePermitsIsNotBlockedBySaturatedOtherOrigin() {
        ConnectionAdmission admission = new ConnectionAdmission(10, 1);
        List<String> started = new ArrayList<String>();

        admission.acquire("a", () -> started.add("a1"));
        admission.acquire("a", () -> started.add("a2"));
        admission.acquire("b", () -> started.add("b1"));

        assertThat(started).containsExactly("a1", "b1");
    }

    @Test
    void drainsManyImmediatelyReleasingWaitersWithoutRecursion() {
        ConnectionAdmission admission = new ConnectionAdmission(1, 1);
        int[] ran = new int[1];
        admission.acquire("a", () -> { });
        for (int i = 0; i < 100_000; i++) {
            admission.acquire("a", () -> {
                ran[0]++;
                admission.release("a");
            });
        }

        admission.release("a");

        assertThat(ran[0]).isEqualTo(100_000);
        assertThat(admission.waiting()).isZero();
        assertThat(admission.inFlight()).isZero();
    }

    @Test
    void withdrawnTicketsAreNeverGranted() {
        ConnectionAdmission admission = new ConnectionAdmission(1, 1);
        List<String> started = new ArrayList<String>();
        assertThat(admission.acquire("a", () -> started.add("a1"))).isNull();
        ConnectionAdmission.Ticket ticket = admission.acquire("a", () -> started.add("a2"));

        admission.withdraw(ticket);
        admission.release("a");

        assertThat(started).containsExactly("a1");
        assertThat(admission.inFlight()).isZero();
    }
}
