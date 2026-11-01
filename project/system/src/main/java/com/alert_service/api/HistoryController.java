package com.alert_service.api;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.alert_service.bars.BarMessage;
import com.alert_service.bars.BarStore;
import com.alert_service.bars.BarStore.Snapshot;
import com.alert_service.marketdata.Topic;
import com.alert_service.signal.SignalService;
import com.alert_service.signal.SignalService.SignalHistory;

/**
 * What a chart needs when it opens during the day: the bars and signals already emitted. A client subscribes to the
 * live topic first, then calls these, and drops duplicates (bars by {@code time}, signals by {@code id}).
 */
@RestController
@RequestMapping("/api")
public class HistoryController {

	private final BarStore bars;
	private final SignalService signals;

	public HistoryController(BarStore bars, SignalService signals) {
		this.bars = bars;
		this.signals = signals;
	}

	public record BarHistory(String symbol, LocalDate tradingDay, List<BarMessage> bars) {
	}

	/**
	 * Today's stored 1-second bars of {@code symbol}, optionally from {@code from} (ISO-8601 UTC, inclusive).
	 */
	@GetMapping("/bars/{symbol}")
	public BarHistory bars(@PathVariable String symbol, @RequestParam(required = false) String from) {
		Topic.requireValidSymbol(symbol);
		Snapshot snapshot = bars.since(symbol, parseInstant(from));
		return new BarHistory(symbol, snapshot.tradingDay(), snapshot.bars().stream().map(BarMessage::bar).toList());
	}

	/**
	 * Day status and today's signals ({@link SignalHistory}) of a running rule; 404 if nobody runs it.
	 * URL-encode the key.
	 */
	@GetMapping("/signals/{ruleKey}")
	public ResponseEntity<?> signals(@PathVariable String ruleKey) {
		return signals.history(ruleKey)
			.<ResponseEntity<?>>map(ResponseEntity::ok)
			.orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(Map.of("error", "Rule " + ruleKey + " is not running; subscribe to "
						+ StompConfig.SIGNALS_TOPIC + ruleKey + " first")));
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException ex) {
		return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
	}

	private static Instant parseInstant(String from) {
		if (from == null || from.isBlank()) {
			return null;
		}
		try {
			return Instant.parse(from);
		}
		catch (DateTimeParseException ex) {
			throw new IllegalArgumentException("'from' must be an ISO-8601 UTC instant, e.g. 2026-10-12T13:45:00Z");
		}
	}
}
