package com.alert_service.run;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.alert_service.rule.RuleKey;
import com.alert_service.rule.RuleRegistry;
import com.alert_service.run.SessionSubscriptions.Ref;
import com.alert_service.signal.SignalService;
import com.alert_service.signal.SignalService.SignalStream;

/**
 * Runs rules for the users subscribed to them. A run is one client subscription to a rule's signal topic: the first
 * run of a rule makes {@link SignalService} attach to the aggregate; the last run leaving detaches it, which tears
 * the rule down.
 */
@Service
public class RunService {

	private static final Pattern RULE_KEY = Pattern.compile("([^\\[\\]]+)\\[([^\\[\\]]*)]");

	private final RuleRegistry registry;
	private final SignalService signals;
	private final SessionSubscriptions<SignalStream> runs = new SessionSubscriptions<>(SignalStream::close);

	public RunService(RuleRegistry registry, SignalService signals) {
		this.registry = registry;
		this.signals = signals;
	}

	/**
	 * Starts, or joins, the rule named by {@code ruleKey}, e.g. {@code SEQUENCE_RANGE_SWEEP[ES,NQ,RTY,YM]}.
	 *
	 * @throws IllegalArgumentException if the key is malformed, names an unknown rule or invalid symbols, or is not in
	 *                                  canonical form (symbols sorted and unique)
	 */
	public void subscribe(Ref ref, String ruleKey) {
		RuleKey key = canonicalKey(ruleKey);
		runs.acquire(ref, key.toString(), () -> signals.attach(key));
	}

	/** @return whether {@code ref} was running a rule */
	public boolean unsubscribe(Ref ref) {
		return runs.release(ref);
	}

	public void unsubscribeAll(String sessionId) {
		runs.releaseSession(sessionId);
	}

	/** Rule key to the sessions running it. */
	public Map<String, Set<String>> runs() {
		return runs.holders();
	}

	RuleKey canonicalKey(String text) {
		Matcher matcher = RULE_KEY.matcher(text == null ? "" : text);
		if (!matcher.matches()) {
			throw new IllegalArgumentException("Malformed rule key '" + text + "', expected TYPE[SYMBOL,...]");
		}
		List<String> symbols = matcher.group(2).isEmpty() ? List.of() : Arrays.asList(matcher.group(2).split(",", -1));
		RuleKey key = registry.keyFor(matcher.group(1), symbols);
		if (!key.toString().equals(text)) {
			throw new IllegalArgumentException(
					"Rule key '" + text + "' is not in canonical form (symbols sorted and unique): use " + key);
		}
		return key;
	}
}
