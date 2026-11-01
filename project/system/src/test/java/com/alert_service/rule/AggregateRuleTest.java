package com.alert_service.rule;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class AggregateRuleTest {

	private static final LocalDate DAY = LocalDate.of(2026, 10, 12);

	/** Counts events and records whether two were ever processed at the same time. */
	private static final class CountingRule extends AggregateRule<Integer, CountingRule.State> {

		private final AtomicInteger inProgress = new AtomicInteger();
		private final AtomicInteger overlaps = new AtomicInteger();
		private final CountDownLatch done;
		private final List<Integer> seen = new ArrayList<>();

		CountingRule(int expected) {
			super(RuleKey.of("COUNTING", "ES"));
			this.done = new CountDownLatch(expected);
		}

		@Override
		protected LocalDate tradingDayOf(Integer event) {
			return DAY;
		}

		@Override
		protected State newState(LocalDate tradingDay) {
			return new State(tradingDay);
		}

		@Override
		protected void process(Integer event) {
			if (inProgress.incrementAndGet() > 1) {
				overlaps.incrementAndGet();
			}
			if (event < 0) {
				inProgress.decrementAndGet();
				throw new IllegalStateException("bad event");
			}
			seen.add(event);
			Thread.yield();
			inProgress.decrementAndGet();
			done.countDown();
		}

		record State(LocalDate tradingDay) implements RuleState {
		}
	}

	@Test
	void eventsFromManyThreadsAreProcessedOneAtATime() throws InterruptedException {
		int threads = 4;
		int perThread = 500;
		CountingRule rule = new CountingRule(threads * perThread);
		ExecutorService producers = Executors.newFixedThreadPool(threads);
		for (int t = 0; t < threads; t++) {
			producers.submit(() -> {
				for (int i = 0; i < perThread; i++) {
					rule.inputListener().onEvent(i);
				}
			});
		}

		assertThat(rule.done.await(10, TimeUnit.SECONDS)).isTrue();
		producers.shutdown();
		assertThat(rule.overlaps).hasValue(0);
		assertThat(rule.seen).hasSize(threads * perThread);
		rule.close();
	}

	@Test
	void failingEventDoesNotStopTheQueue() throws InterruptedException {
		CountingRule rule = new CountingRule(1);

		rule.enqueue(-1);
		rule.enqueue(7);

		assertThat(rule.done.await(5, TimeUnit.SECONDS)).isTrue();
		assertThat(rule.seen).containsExactly(7);
		rule.close();
	}

	@Test
	void eventsAfterCloseAreDropped() {
		CountingRule rule = new CountingRule(1);
		rule.close();

		rule.enqueue(1);

		assertThat(rule.seen).isEmpty();
	}
}
