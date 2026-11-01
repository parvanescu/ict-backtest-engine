package com.alert_service.rule;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.alert_service.event.Listener;
import com.alert_service.event.Subscription;
import com.alert_service.marketdata.OhlcvBar;
import com.alert_service.marketdata.Topic;
import com.alert_service.marketdata.zmq.ProviderManager;

/**
 * Holds every running rule instance by key, creates them bottom-up (providers, basic rules, aggregate) and
 * retires them when their last listener leaves, which cascades down to the providers.
 * Get-or-create-and-subscribe and retire-when-unused run under one lock.
 */
public class RuleRegistry {

	private final Map<String, BasicRuleFactory> basicFactories;
	private final Map<String, AggregateRuleFactory> aggregateFactories;
	private final ProviderManager providers;
	private final Map<RuleKey, BasicRule<?, ?>> basics = new HashMap<>();
	private final Map<RuleKey, AggregateRule<?, ?>> aggregates = new HashMap<>();

	public RuleRegistry(List<BasicRuleFactory> basicFactories, List<AggregateRuleFactory> aggregateFactories,
			ProviderManager providers) {
		this.basicFactories = basicFactories.stream()
			.collect(Collectors.toUnmodifiableMap(f -> f.descriptor().type(), f -> f));
		this.aggregateFactories = aggregateFactories.stream()
			.collect(Collectors.toUnmodifiableMap(f -> f.descriptor().type(), f -> f));
		this.providers = providers;
		this.aggregateFactories.values().forEach(f -> checkWiring(f.descriptor()));
	}

	private void checkWiring(AggregateRuleDescriptor aggregate) {
		BasicRuleFactory basic = basicFactories.get(aggregate.basicRuleType());
		if (basic == null) {
			throw new IllegalStateException(aggregate.type() + " needs unknown basic rule " + aggregate.basicRuleType());
		}
		if (!aggregate.inputType().isAssignableFrom(basic.descriptor().eventType())) {
			throw new IllegalStateException(aggregate.type() + " consumes " + aggregate.inputType().getSimpleName()
					+ " but " + basic.descriptor().type() + " emits " + basic.descriptor().eventType().getSimpleName());
		}
	}

	public Set<String> aggregateTypes() {
		return aggregateFactories.keySet();
	}

	/**
	 * Validates a request and returns the key of the aggregate it names.
	 *
	 * @throws IllegalArgumentException for an unknown rule, an invalid symbol, or too few symbols
	 */
	public RuleKey keyFor(String type, Collection<String> symbols) {
		AggregateRuleFactory factory = aggregateFactories.get(type);
		if (factory == null) {
			throw new IllegalArgumentException("Unknown rule " + type + ", known: " + aggregateFactories.keySet());
		}
		if (symbols == null) {
			throw new IllegalArgumentException("Symbols are required");
		}
		symbols.forEach(Topic::requireValidSymbol);
		RuleKey key = RuleKey.of(type, symbols);
		if (key.symbols().size() < factory.descriptor().minSymbols()) {
			throw new IllegalArgumentException(
					type + " needs at least " + factory.descriptor().minSymbols() + " distinct symbols");
		}
		return key;
	}

	/** Gets or creates the aggregate for {@code key} and subscribes {@code listener} to its results. */
	public synchronized Attachment subscribe(RuleKey key, String subscriberId, Listener<? super RuleResult> listener) {
		AggregateRule<?, ?> aggregate = aggregates.get(key);
		if (aggregate == null) {
			aggregate = createAggregate(key);
		}
		return new Attachment(aggregate, aggregate.subscribe(subscriberId, listener));
	}

	public synchronized Set<RuleKey> activeKeys() {
		Set<RuleKey> keys = new HashSet<>(basics.keySet());
		keys.addAll(aggregates.keySet());
		return Set.copyOf(keys);
	}

	private AggregateRule<?, ?> createAggregate(RuleKey key) {
		AggregateRuleFactory factory = aggregateFactories.get(key.type());
		if (factory == null) {
			throw new IllegalArgumentException("Unknown rule " + key.type());
		}
		AggregateRule<?, ?> aggregate = factory.create(key, key.symbols());
		try {
			for (String symbol : key.symbols()) {
				BasicRule<?, ?> basic = basicFor(RuleKey.of(factory.descriptor().basicRuleType(), symbol), symbol);
				aggregate.addInput(subscribeUnchecked(basic, key.toString(), aggregate.inputListener()));
			}
		}
		catch (RuntimeException ex) {
			aggregate.close();
			retireUnusedBasics();
			throw ex;
		}
		AggregateRule<?, ?> created = aggregate;
		aggregate.whenUnused(() -> retire(created, aggregates));
		aggregates.put(key, aggregate);
		return aggregate;
	}

	private BasicRule<?, ?> basicFor(RuleKey key, String symbol) {
		BasicRule<?, ?> existing = basics.get(key);
		if (existing != null) {
			return existing;
		}
		BasicRuleFactory factory = basicFactories.get(key.type());
		BasicRule<?, ?> basic = factory.create(key, symbol);
		Topic topic = factory.descriptor().data().topicFor(symbol);
		basic.addInput(providers.subscribe(topic, OhlcvBar.class, key.toString(), basic.inputListener()));
		basic.whenUnused(() -> retire(basic, basics));
		basics.put(key, basic);
		return basic;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private static Subscription subscribeUnchecked(BasicRule<?, ?> basic, String subscriberId, Listener<?> listener) {
		// Event types were checked against the descriptors in the constructor.
		return basic.subscribe(subscriberId, (Listener) listener);
	}

	/** Basic rules created for an aggregate that then failed to build never got a listener. */
	private void retireUnusedBasics() {
		List<BasicRule<?, ?>> unused = basics.values().stream().filter(b -> !b.hasListeners()).toList();
		unused.forEach(basic -> retire(basic, basics));
	}

	private synchronized <R extends RuleInstance<?, ?, ?>> void retire(R instance, Map<RuleKey, R> instances) {
		if (!instance.hasListeners() && instances.remove(instance.key(), instance)) {
			instance.close();
		}
	}

	/** An aggregate and one listener's subscription to it. */
	public record Attachment(AggregateRule<?, ?> rule, Subscription subscription) {
	}
}
