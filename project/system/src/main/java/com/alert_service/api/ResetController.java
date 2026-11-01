package com.alert_service.api;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.alert_service.bars.BarStore;
import com.alert_service.signal.SignalService;

/**
 * Wipes the day's data so another run can start clean: the stored bars, the signal history and the state of the
 * running rules (which keep running). The JSON-only body requirement makes a browser send a CORS preflight, so only
 * the allowed origins can call it.
 */
@RestController
@RequestMapping("/api")
public class ResetController {

	private final BarStore bars;
	private final SignalService signals;

	public ResetController(BarStore bars, SignalService signals) {
		this.bars = bars;
		this.signals = signals;
	}

	@PostMapping(value = "/reset", consumes = "application/json")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void reset() {
		bars.clear();
		signals.reset();
	}
}
