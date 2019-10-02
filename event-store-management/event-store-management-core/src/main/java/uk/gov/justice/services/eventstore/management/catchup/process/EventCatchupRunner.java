package uk.gov.justice.services.eventstore.management.catchup.process;

import static java.lang.String.format;

import uk.gov.justice.services.common.util.UtcClock;
import uk.gov.justice.services.eventstore.management.events.catchup.CatchupStartedEvent;
import uk.gov.justice.services.eventstore.management.events.catchup.CatchupType;
import uk.gov.justice.services.jmx.api.command.SystemCommand;
import uk.gov.justice.subscription.domain.subscriptiondescriptor.SubscriptionsDescriptor;
import uk.gov.justice.subscription.registry.SubscriptionsDescriptorsRegistry;

import java.util.List;
import java.util.UUID;

import javax.enterprise.event.Event;
import javax.inject.Inject;

import org.slf4j.Logger;

public class EventCatchupRunner {

    @Inject
    private SubscriptionsDescriptorsRegistry subscriptionsDescriptorsRegistry;

    @Inject
    private Event<CatchupStartedEvent> catchupStartedEventFirer;

    @Inject
    private EventCatchupByComponentRunner eventCatchupByComponentRunner;

    @Inject
    private UtcClock clock;

    @Inject
    private Logger logger;

    public void runEventCatchup(final UUID commandId, final CatchupType catchupType, final SystemCommand systemCommand) {

        logger.info(format("Received CatchupRequestedEvent for %s", catchupType));

        catchupStartedEventFirer.fire(new CatchupStartedEvent(commandId, catchupType, clock.now()));

        final List<SubscriptionsDescriptor> subscriptionsDescriptors =
                subscriptionsDescriptorsRegistry.getAll();

        subscriptionsDescriptors.forEach(subscriptionsDescriptor -> eventCatchupByComponentRunner.runEventCatchupForComponent(
                commandId,
                subscriptionsDescriptor,
                catchupType,
                systemCommand));
    }
}
