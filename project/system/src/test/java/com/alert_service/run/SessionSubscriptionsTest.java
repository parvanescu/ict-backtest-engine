package com.alert_service.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.alert_service.run.SessionSubscriptions.Ref;

class SessionSubscriptionsTest {

	private final AtomicInteger opened = new AtomicInteger();
	private final List<String> closed = new ArrayList<>();
	private final SessionSubscriptions<String> subscriptions = new SessionSubscriptions<>(closed::add);

	private String acquire(String session, String subscription, String key) {
		return subscriptions.acquire(new Ref(session, subscription), key, () -> key + "#" + opened.incrementAndGet());
	}

	@Test
	void firstHolderOpensAndOthersShare() {
		assertThat(acquire("A", "1", "ES")).isEqualTo("ES#1");
		assertThat(acquire("B", "1", "ES")).isEqualTo("ES#1");
		assertThat(acquire("A", "1", "ES")).isEqualTo("ES#1");

		assertThat(opened).hasValue(1);
		assertThat(subscriptions.holders()).isEqualTo(Map.of("ES", Set.of("A", "B")));
	}

	@Test
	void lastHolderLeavingCloses() {
		acquire("A", "1", "ES");
		acquire("B", "7", "ES");

		assertThat(subscriptions.release(new Ref("A", "1"))).isTrue();
		assertThat(closed).isEmpty();
		assertThat(subscriptions.release(new Ref("B", "7"))).isTrue();

		assertThat(closed).containsExactly("ES#1");
		assertThat(subscriptions.holders()).isEmpty();
		assertThat(subscriptions.release(new Ref("B", "7"))).isFalse();
	}

	@Test
	void releaseSessionReleasesAllItsSubscriptions() {
		acquire("A", "1", "ES");
		acquire("A", "2", "NQ");
		acquire("B", "1", "NQ");

		subscriptions.releaseSession("A");

		assertThat(closed).containsExactly("ES#1");
		assertThat(subscriptions.holders()).isEqualTo(Map.of("NQ", Set.of("B")));
	}

	@Test
	void failedOpenRegistersNothing() {
		assertThatIllegalStateException().isThrownBy(() -> subscriptions.acquire(new Ref("A", "1"), "ES", () -> {
			throw new IllegalStateException("cannot open");
		}));

		assertThat(subscriptions.holders()).isEmpty();
		assertThat(subscriptions.release(new Ref("A", "1"))).isFalse();
	}

	@Test
	void reusedSubscriptionIdForAnotherKeyMovesTheHold() {
		acquire("A", "1", "ES");
		acquire("A", "1", "NQ");

		assertThat(closed).containsExactly("ES#1");
		assertThat(subscriptions.holders()).isEqualTo(Map.of("NQ", Set.of("A")));
	}
}
