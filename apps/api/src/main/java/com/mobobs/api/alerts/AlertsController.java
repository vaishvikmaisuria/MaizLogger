package com.mobobs.api.alerts;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;

import static com.mobobs.api.alerts.AlertDtos.*;

/**
 * Read-only alert endpoints.
 *
 * <ul>
 *   <li>{@code GET /v1/alerts/rules} — list configured alert rules</li>
 *   <li>{@code GET /v1/alerts/feed}  — recent alert firings</li>
 * </ul>
 */
@RestController
@RequestMapping("/v1/alerts")
public class AlertsController {

    private static final int MAX_LIMIT = 500;

    private final AlertRepository alertRepository;

    public AlertsController(AlertRepository alertRepository) {
        this.alertRepository = alertRepository;
    }

    /**
     * Lists all configured alert rules.
     *
     * @param app optional app-name filter (omit or leave blank for all apps)
     */
    @GetMapping("/rules")
    public List<AlertRuleDto> rules(
            @RequestParam(defaultValue = "") String app) {
        return alertRepository.findRulesForApp(app.isBlank() ? null : app);
    }

    /**
     * Returns recent alert firings, newest first.
     *
     * @param app   optional app-name filter
     * @param from  earliest firing timestamp (default: 24 h ago)
     * @param to    latest  firing timestamp  (default: now)
     * @param limit maximum rows returned — capped at 500
     */
    @GetMapping("/feed")
    public AlertFeedResponse feed(
            @RequestParam(defaultValue = "") String app,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(defaultValue = "50") int limit) {

        OffsetDateTime effectiveTo   = to   != null ? to   : OffsetDateTime.now();
        OffsetDateTime effectiveFrom = from != null ? from : effectiveTo.minusHours(24);
        int effectiveLimit = Math.min(limit, MAX_LIMIT);

        List<AlertFiringDto> firings = alertRepository.findRecentFirings(
                app.isBlank() ? null : app, effectiveFrom, effectiveTo, effectiveLimit);

        return new AlertFeedResponse(firings.size(), firings);
    }
}
