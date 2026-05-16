package com.warehouse.producer.controller;

import com.warehouse.producer.model.EventResponse;
import com.warehouse.producer.model.WarehouseErrorEvent;
import com.warehouse.producer.service.EventProducerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class EventController {

    private final EventProducerService eventProducerService;

    /**
     * Accepts a warehouse error event and publishes it to Kafka.
     *
     * Minimum required fields: deviceId, warehouseZone, errorCode, severity.
     * eventId and timestamp are auto-generated if omitted.
     *
     * Returns 202 Accepted immediately — delivery confirmation is async.
     * The eventId in the response can be used to trace the event through
     * Kafka, the alert-processor logs, and the alerts DB table.
     */
    @PostMapping
    public ResponseEntity<EventResponse> publishEvent(
            @Valid @RequestBody WarehouseErrorEvent event) {

        log.debug("Received publish request: deviceId={} errorCode={} severity={}",
                event.getDeviceId(), event.getErrorCode(), event.getSeverity());

        EventResponse response = eventProducerService.publish(event);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }
}
