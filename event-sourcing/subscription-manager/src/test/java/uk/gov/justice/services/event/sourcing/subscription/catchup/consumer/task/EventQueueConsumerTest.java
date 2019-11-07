package uk.gov.justice.services.event.sourcing.subscription.catchup.consumer.task;

import static java.util.UUID.randomUUID;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import uk.gov.justice.services.event.sourcing.subscription.catchup.consumer.manager.EventStreamConsumptionResolver;
import uk.gov.justice.services.event.sourcing.subscription.catchup.consumer.manager.FinishedProcessingMessage;
import uk.gov.justice.services.event.sourcing.subscription.manager.TransactionalEventProcessor;
import uk.gov.justice.services.eventsourcing.repository.jdbc.event.PublishedEvent;
import uk.gov.justice.services.eventstore.management.commands.CatchupCommand;
import uk.gov.justice.services.eventstore.management.commands.EventCatchupCommand;

import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class EventQueueConsumerTest {

    @Mock
    private TransactionalEventProcessor transactionalEventProcessor;

    @Mock
    private EventStreamConsumptionResolver eventStreamConsumptionResolver;

    @Mock
    private EventProcessingFailedHandler eventProcessingFailedHandler;


    @InjectMocks
    private EventQueueConsumer eventQueueConsumer;

    @Test
    public void shouldProcessAllEventsOnQueueAndReturnTrueIfComplete() throws Exception {

        final UUID commandId = randomUUID();
        final CatchupCommand catchupCommand = new EventCatchupCommand();
        
        final PublishedEvent event_1 = mock(PublishedEvent.class);
        final PublishedEvent event_2 = mock(PublishedEvent.class);

        final Queue<PublishedEvent> eventQueue = new ConcurrentLinkedQueue<>();

        when(eventStreamConsumptionResolver.isEventConsumptionComplete(new FinishedProcessingMessage(eventQueue))).thenReturn(true);

        eventQueue.add(event_1);
        eventQueue.add(event_2);
        final String subscriptionName = "subscriptionName";

        eventQueueConsumer.consumeEventQueue(commandId, eventQueue, subscriptionName, catchupCommand);

        final InOrder inOrder = inOrder(transactionalEventProcessor);

        inOrder.verify(transactionalEventProcessor).processWithEventBuffer(event_1, subscriptionName);
        inOrder.verify(transactionalEventProcessor).processWithEventBuffer(event_2, subscriptionName);
    }

    @Test
    public void shouldHandleExceptionsThrownWhilstProcessing() throws Exception {

        final NullPointerException nullPointerException = new NullPointerException("Ooops");

        final CatchupCommand catchupCommand = new EventCatchupCommand();
        final UUID commandId = randomUUID();
        final PublishedEvent event_1 = mock(PublishedEvent.class);
        final String metadata = "{some: metadata}";
        final PublishedEvent event_2 = mock(PublishedEvent.class);

        final Queue<PublishedEvent> eventQueue = new ConcurrentLinkedQueue<>();

        when(eventStreamConsumptionResolver.isEventConsumptionComplete(new FinishedProcessingMessage(eventQueue))).thenReturn(true);
        when(event_2.getMetadata()).thenReturn(metadata);

        eventQueue.add(event_1);
        eventQueue.add(event_2);
        final String subscriptionName = "subscriptionName";

        doThrow(nullPointerException).when(transactionalEventProcessor).processWithEventBuffer(event_1, subscriptionName);

        eventQueueConsumer.consumeEventQueue(commandId, eventQueue, subscriptionName, catchupCommand);

        verify(transactionalEventProcessor).processWithEventBuffer(event_2, subscriptionName);

        verify(eventProcessingFailedHandler).handle(
                nullPointerException,
                event_1,
                subscriptionName,
                catchupCommand,
                commandId
        );
    }
}
