package com.playwrightforkubejs;

import com.playwrightforkubejs.protocol.ErrorCode;
import com.playwrightforkubejs.protocol.EventBus;
import com.playwrightforkubejs.protocol.Params;
import com.playwrightforkubejs.protocol.PlaywrightException;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class CoreContractTest {
    @Test
    void parameterReadersAcceptNumbersAndRejectInvalidValues() {
        assertEquals(4, Params.integer(Map.of("slot", 4), "slot", -1));
        assertEquals(2.5, Params.number(Map.of("radius", 2.5), "radius", 1.0));
        assertThrows(PlaywrightException.class, () -> Params.integer(Map.of("slot", "bad"), "slot", -1));
    }

    @Test
    void eventWaitCanObserveAnEventEvenAfterTheResponseQueueWasDrained() {
        EventBus bus = EventBus.getInstance();
        String name = "test." + UUID.randomUUID();
        long expectedSequence = bus.nextSequence();
        bus.record(name, Map.of("value", 7));
        bus.drain();

        Map<String, Object> event = bus.eventAfter(name, expectedSequence);
        assertNotNull(event);
        assertEquals(name, event.get("name"));
        assertEquals(Map.of("value", 7), event.get("data"));
    }

    @Test
    void publicErrorsKeepStableCodes() {
        PlaywrightException exception = new PlaywrightException(ErrorCode.TIMEOUT, "wait expired");
        assertEquals("TIMEOUT", exception.getCodeName());
        assertEquals(ErrorCode.TIMEOUT, exception.getCode());
    }
}
