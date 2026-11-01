package com.alert_service.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class ListenerListTest {

	private final AtomicInteger emptied = new AtomicInteger();
	private final ListenerList<String> list = new ListenerList<>("test", emptied::incrementAndGet);

	@Test
	void dispatchReachesEveryListener() {
		List<String> a = new ArrayList<>();
		List<String> b = new ArrayList<>();
		list.subscribe("a", a::add);
		list.subscribe("b", b::add);

		list.dispatch("x");

		assertThat(a).containsExactly("x");
		assertThat(b).containsExactly("x");
	}

	@Test
	void sameSubscriberIdIsIdempotent() {
		List<String> received = new ArrayList<>();
		Subscription first = list.subscribe("a", received::add);
		Subscription second = list.subscribe("a", received::add);

		list.dispatch("x");

		assertThat(second).isSameAs(first);
		assertThat(list.size()).isEqualTo(1);
		assertThat(received).containsExactly("x");
	}

	@Test
	void closedSubscriptionStopsReceiving() {
		List<String> received = new ArrayList<>();
		Subscription subscription = list.subscribe("a", received::add);

		subscription.close();
		list.dispatch("x");

		assertThat(subscription.isClosed()).isTrue();
		assertThat(received).isEmpty();
	}

	@Test
	void onEmptyRunsOnceWhenLastListenerLeaves() {
		Subscription a = list.subscribe("a", e -> {
		});
		Subscription b = list.subscribe("b", e -> {
		});

		a.close();
		assertThat(emptied).hasValue(0);
		b.close();
		b.close();
		assertThat(emptied).hasValue(1);
		assertThat(list.isEmpty()).isTrue();
	}

	@Test
	void failingListenerDoesNotStopOthers() {
		List<String> received = new ArrayList<>();
		list.subscribe("broken", e -> {
			throw new IllegalStateException("boom");
		});
		list.subscribe("ok", received::add);

		list.dispatch("x");

		assertThat(received).containsExactly("x");
	}

	@Test
	void listenerAddedDuringDispatchReceivesFromNextEvent() {
		List<String> late = new ArrayList<>();
		list.subscribe("adder", e -> list.subscribe("late", late::add));

		list.dispatch("first");
		list.dispatch("second");

		assertThat(late).containsExactly("second");
	}

	@Test
	void concurrentSubscribeAndCloseKeepsListConsistent() throws InterruptedException {
		int threads = 8;
		int perThread = 200;
		ExecutorService pool = Executors.newFixedThreadPool(threads);
		CountDownLatch start = new CountDownLatch(1);
		for (int t = 0; t < threads; t++) {
			int thread = t;
			pool.submit(() -> {
				start.await();
				for (int i = 0; i < perThread; i++) {
					Subscription s = list.subscribe(thread + "-" + i, e -> {
					});
					list.dispatch("x");
					if (i % 2 == 0) {
						s.close();
					}
				}
				return null;
			});
		}
		start.countDown();
		pool.shutdown();

		assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
		assertThat(list.size()).isEqualTo(threads * perThread / 2);
	}
}
