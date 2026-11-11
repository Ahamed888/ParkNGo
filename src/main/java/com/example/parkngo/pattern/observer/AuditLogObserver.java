package com.example.parkngo.pattern.observer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** ConcreteObserver that records a lightweight audit trail without database writes. */
// [VIVA - Concrete Observer 2]: Listens for system events and writes audit trail logs independently of business workflows.
@Component
public class AuditLogObserver implements Observer {
    private static final Logger log = LoggerFactory.getLogger(AuditLogObserver.class);

    @Override
    public void update(ParkingEvent event) {
        log.info("Parking event published: type={} recipientId={} referenceId={}",
                event.getType(), event.getRecipientId(), event.getReferenceId());
    }
}
