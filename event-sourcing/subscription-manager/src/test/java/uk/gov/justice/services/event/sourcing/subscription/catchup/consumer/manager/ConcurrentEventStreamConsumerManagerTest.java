package uk.gov.justice.services.event.sourcing.subscription.catchup.consumer.manager;

import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import uk.gov.justice.services.event.sourcing.subscription.catchup.consumer.task.ConsumeEventQueueTaskManager;
import uk.gov.justice.services.event.sourcing.subscription.catchup.consumer.task.EventQueueConsumer;
import uk.gov.justice.services.eventsourcing.repository.jdbc.event.PublishedEvent;
import uk.gov.justice.services.eventstore.management.commands.CatchupCommand;
import uk.gov.justice.services.eventstore.management.commands.EventCatchupCommand;

import java.util.List;
import java.util.Queue;
import java.util.UUID;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ConcurrentEventStreamConsumerManagerTest {

    @Mock
    private ConsumeEventQueueTaskManager consumeEventQueueTaskManager;

    @Mock
    private EventQueueConsumerFactory eventQueueConsumerFactory;

    @Spy
    private EventStreamsInProgressList eventStreamsInProgressList = new EventStreamsInProgressList();

    @InjectMocks
    private ConcurrentEventStreamConsumerManager concurrentEventStreamConsumerManager;

    @Captor
    private ArgumentCaptor<Queue<PublishedEvent>> eventQueueCaptor;

    @Test
    public void shouldCreateQueueAndCreateTaskToConsumeQueueForNewStreamId() {

        final CatchupCommand catchupCommand = new EventCatchupCommand();
        final UUID commandId = randomUUID();
        final String subscriptionName = "subscriptionName";
        final UUID streamId = randomUUID();
        final PublishedEvent publishedEvent = mock(PublishedEvent.class);

        final EventQueueConsumer eventQueueConsumer = mock(EventQueueConsumer.class);

        when(eventQueueConsumerFactory.create(concurrentEventStreamConsumerManager)).thenReturn(eventQueueConsumer);
        when(publishedEvent.getStreamId()).thenReturn(streamId);

        concurrentEventStreamConsumerManager.add(publishedEvent, subscriptionName, catchupCommand, commandId);

        verify(consumeEventQueueTaskManager).consume(eventQueueCaptor.capture(), eq(eventQueueConsumer), eq(subscriptionName), eq(catchupCommand), eq(commandId));

        final Queue<PublishedEvent> events = eventQueueCaptor.getValue();
        assertThat(events.size(), is(1));
        assertThat(events.poll(), is(publishedEvent));

    }

    @Test
    public void shouldNotCreateQueueOrCreateTaskIfEventIsSameStreamId() {

        final UUID commandId = randomUUID();
        final String subscriptionName = "subscriptionName";
        final UUID streamId = randomUUID();
        final PublishedEvent publishedEvent_1 = mock(PublishedEvent.class);
        final PublishedEvent publishedEvent_2 = mock(PublishedEvent.class);
        final EventQueueConsumer eventQueueConsumer = mock(EventQueueConsumer.class);
        final CatchupCommand catchupCommand = new EventCatchupCommand();

        when(eventQueueConsumerFactory.create(concurrentEventStreamConsumerManager)).thenReturn(eventQueueConsumer);
        when(publishedEvent_1.getStreamId()).thenReturn(streamId);
        when(publishedEvent_2.getStreamId()).thenReturn(streamId);

        concurrentEventStreamConsumerManager.add(publishedEvent_1, subscriptionName, catchupCommand, commandId);
        concurrentEventStreamConsumerManager.add(publishedEvent_2, subscriptionName, catchupCommand, commandId);

        verify(consumeEventQueueTaskManager).consume(eventQueueCaptor.capture(), eq(eventQueueConsumer), eq(subscriptionName), eq(catchupCommand), eq(commandId));

        final Queue<PublishedEvent> eventsStream = eventQueueCaptor.getValue();
        assertThat(eventsStream.size(), is(2));
        assertThat(eventsStream.poll(), is(publishedEvent_1));
        assertThat(eventsStream.poll(), is(publishedEvent_2));
    }

    @Test
    public void shouldCreateQueueForEachStreamId() {

        final CatchupCommand catchupCommand = new EventCatchupCommand();
        final UUID commandId = randomUUID();
        final String subscriptionName = "subscriptionName";
        final UUID streamId_1 = randomUUID();
        final UUID streamId_2 = randomUUID();
        final PublishedEvent publishedEvent_1 = mock(PublishedEvent.class);
        final PublishedEvent publishedEvent_2 = mock(PublishedEvent.class);
        final EventQueueConsumer eventQueueConsumer = mock(EventQueueConsumer.class);

        when(eventQueueConsumerFactory.create(concurrentEventStreamConsumerManager)).thenReturn(eventQueueConsumer);
        when(publishedEvent_1.getStreamId()).thenReturn(streamId_1);
        when(publishedEvent_2.getStreamId()).thenReturn(streamId_2);

        concurrentEventStreamConsumerManager.add(publishedEvent_1, subscriptionName, catchupCommand, commandId);
        concurrentEventStreamConsumerManager.add(publishedEvent_2, subscriptionName, catchupCommand, commandId);

        verify(consumeEventQueueTaskManager, times(2)).consume(eventQueueCaptor.capture(), eq(eventQueueConsumer), eq(subscriptionName), eq(catchupCommand), eq(commandId));

        final List<Queue<PublishedEvent>> allValues = eventQueueCaptor.getAllValues();

        final Queue<PublishedEvent> eventsStream_1 = allValues.get(0);
        assertThat(eventsStream_1.size(), is(1));
        assertThat(eventsStream_1.poll(), is(publishedEvent_1));

        final Queue<PublishedEvent> eventsStream_2 = allValues.get(1);
        assertThat(eventsStream_2.size(), is(1));
        assertThat(eventsStream_2.poll(), is(publishedEvent_2));
    }

    @Test
    public void shouldBeAbleToFinishQueueAndAllowAnotherProcessToPickupQueueIfNotEmpty() {

        final CatchupCommand catchupCommand = new EventCatchupCommand();
        final UUID commandId = randomUUID();
        final String subscriptionName = "subscriptionName";
        final UUID streamId_1 = randomUUID();
        final UUID streamId_2 = randomUUID();
        final PublishedEvent publishedEvent_1 = mock(PublishedEvent.class);
        final PublishedEvent publishedEvent_2 = mock(PublishedEvent.class);
        final EventQueueConsumer eventQueueConsumer = mock(EventQueueConsumer.class);

        when(eventQueueConsumerFactory.create(concurrentEventStreamConsumerManager)).thenReturn(eventQueueConsumer);
        when(publishedEvent_1.getStreamId()).thenReturn(streamId_1);
        when(publishedEvent_2.getStreamId()).thenReturn(streamId_2);

        concurrentEventStreamConsumerManager.add(publishedEvent_1, subscriptionName, catchupCommand, commandId);

        verify(consumeEventQueueTaskManager).consume(eventQueueCaptor.capture(), eq(eventQueueConsumer), eq(subscriptionName), eq(catchupCommand), eq(commandId));

        final Queue<PublishedEvent> eventsStream_1 = eventQueueCaptor.getValue();
        assertThat(eventsStream_1.size(), is(1));
        assertThat(eventsStream_1.poll(), is(publishedEvent_1));

        concurrentEventStreamConsumerManager.isEventConsumptionComplete(new FinishedProcessingMessage(eventsStream_1));
        concurrentEventStreamConsumerManager.add(publishedEvent_2, subscriptionName, catchupCommand, commandId);

        verify(consumeEventQueueTaskManager, times(2)).consume(eventQueueCaptor.capture(), eq(eventQueueConsumer), eq(subscriptionName), eq(catchupCommand), eq(commandId));

        final Queue<PublishedEvent> eventsStream_2 = eventQueueCaptor.getValue();
        assertThat(eventsStream_2.size(), is(1));
        assertThat(eventsStream_2.poll(), is(publishedEvent_2));
    }

    @Test
    public void shouldBlockOnTheEventsStreamInProgressListWhenWaitingForCompletion() throws Exception {

        concurrentEventStreamConsumerManager.waitForCompletion();

        verify(eventStreamsInProgressList).blockUntilEmpty();
    }
}
