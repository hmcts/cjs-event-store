package uk.gov.justice.services.eventstore.management.catchup.observers;

import static java.time.ZoneOffset.UTC;
import static java.time.ZonedDateTime.of;
import static java.time.temporal.ChronoUnit.MILLIS;
import static java.time.temporal.ChronoUnit.SECONDS;
import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static uk.gov.justice.services.jmx.api.domain.CommandState.COMMAND_IN_PROGRESS;

import uk.gov.justice.services.common.util.UtcClock;
import uk.gov.justice.services.eventstore.management.catchup.process.CatchupDurationCalculator;
import uk.gov.justice.services.eventstore.management.catchup.process.CatchupInProgress;
import uk.gov.justice.services.eventstore.management.catchup.process.EventCatchupRunner;
import uk.gov.justice.services.eventstore.management.catchup.state.CatchupError;
import uk.gov.justice.services.eventstore.management.catchup.state.CatchupErrorStateManager;
import uk.gov.justice.services.eventstore.management.catchup.state.CatchupStateManager;
import uk.gov.justice.services.eventstore.management.commands.CatchupCommand;
import uk.gov.justice.services.eventstore.management.commands.EventCatchupCommand;
import uk.gov.justice.services.eventstore.management.events.catchup.CatchupCompletedForSubscriptionEvent;
import uk.gov.justice.services.eventstore.management.events.catchup.CatchupProcessingOfEventFailedEvent;
import uk.gov.justice.services.eventstore.management.events.catchup.CatchupRequestedEvent;
import uk.gov.justice.services.eventstore.management.events.catchup.CatchupStartedForSubscriptionEvent;
import uk.gov.justice.services.jmx.state.events.SystemCommandStateChangedEvent;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.UUID;

import javax.enterprise.event.Event;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.slf4j.Logger;

@RunWith(MockitoJUnitRunner.class)
public class CatchupObserverTest {


    @Mock
    private EventCatchupRunner eventCatchupRunner;

    @Mock
    private CatchupStateManager catchupStateManager;

    @Mock
    private CatchupErrorStateManager catchupErrorStateManager;

    @Mock
    private CatchupDurationCalculator catchupDurationCalculator;

    @Mock
    private UtcClock clock;

    @Mock
    private Event<SystemCommandStateChangedEvent> systemCommandStateChangedEventFirer;

    @Mock
    private CatchupProcessCompleter catchupProcessCompleter;

    @Mock
    private Logger logger;

    @Captor
    private ArgumentCaptor<CatchupInProgress> catchupInProgressCaptor;

    @Captor
    private ArgumentCaptor<CatchupCommand> catchupCommandCaptor;

    @Captor
    private ArgumentCaptor<CatchupError> catchupErrorCaptor;

    @Captor
    private ArgumentCaptor<SystemCommandStateChangedEvent> systemCommandStateChangedEventCaptor;

    @InjectMocks
    private CatchupObserver catchupObserver;

    @Test
    public void shouldStartCatchupOnCatchupRequested() throws Exception {

        final UUID commandId = randomUUID();
        final ZonedDateTime catchupStartedAt = of(2019, 2, 23, 17, 12, 23, 0, UTC);

        final EventCatchupCommand eventCatchupCommand = new EventCatchupCommand();
        final CatchupRequestedEvent catchupRequestedEvent = new CatchupRequestedEvent(
                commandId,
                eventCatchupCommand,
                catchupStartedAt
        );

        when(clock.now()).thenReturn(catchupStartedAt);

        catchupObserver.onCatchupRequested(catchupRequestedEvent);


        final InOrder inOrder = inOrder(
                logger,
                catchupStateManager,
                catchupErrorStateManager,
                systemCommandStateChangedEventFirer,
                eventCatchupRunner);

        inOrder.verify(catchupStateManager).clear(eventCatchupCommand);
        inOrder.verify(catchupErrorStateManager).clear(eventCatchupCommand);
        inOrder.verify(systemCommandStateChangedEventFirer).fire(systemCommandStateChangedEventCaptor.capture());
        inOrder.verify(logger).info("CATCHUP started at 2019-02-23T17:12:23Z");
        inOrder.verify(eventCatchupRunner).runEventCatchup(commandId, eventCatchupCommand);

        final SystemCommandStateChangedEvent stateChangedEvent = systemCommandStateChangedEventCaptor.getValue();

        assertThat(stateChangedEvent.getCommandId(), is(commandId));
        assertThat(stateChangedEvent.getCommandState(), is(COMMAND_IN_PROGRESS));
        assertThat(stateChangedEvent.getStatusChangedAt(), is(catchupStartedAt));
        assertThat(stateChangedEvent.getSystemCommand(), is(eventCatchupCommand));
        assertThat(stateChangedEvent.getMessage(), is("CATCHUP started at 2019-02-23T17:12:23Z"));
    }

    @Test
    public void shouldLogCatchupStartedForSubscriptionAndStoreProgress() throws Exception {

        final EventCatchupCommand eventCatchupCommand = new EventCatchupCommand();
        final UUID commandId = randomUUID();
        final String subscriptionName = "mySubscription";
        final ZonedDateTime catchupStartedAt = of(2019, 2, 23, 17, 12, 23, 0, UTC);

        final CatchupStartedForSubscriptionEvent catchupStartedForSubscriptionEvent = new CatchupStartedForSubscriptionEvent(
                commandId,
                subscriptionName,
                eventCatchupCommand,
                catchupStartedAt);
        catchupObserver.onCatchupStartedForSubscription(catchupStartedForSubscriptionEvent);

        verify(catchupStateManager).addCatchupInProgress(catchupInProgressCaptor.capture(), catchupCommandCaptor.capture());
        verify(logger).info("CATCHUP for subscription 'mySubscription' started at 2019-02-23T17:12:23Z");

        final CatchupInProgress catchupInProgress = catchupInProgressCaptor.getValue();

        assertThat(catchupInProgress.getSubscriptionName(), is(subscriptionName));
        assertThat(catchupInProgress.getStartedAt(), is(catchupStartedAt));

        assertThat(catchupCommandCaptor.getValue(), is(eventCatchupCommand));
    }

    @Test
    public void shouldRemoveTheCatchupForSubscriptionInProgressOnCatchupForSubscriptionComplete() throws Exception {

        final UUID commandId = randomUUID();
        final String subscriptionName = "mySubscription";
        final String eventSourceName = "myEventSource";
        final String componentName = "EVENT_LISTENER";

        final ZonedDateTime catchupCompletedAt = of(2019, 2, 23, 17, 12, 23, 0, UTC);
        final ZonedDateTime catchupStartedAt = catchupCompletedAt.minusSeconds(23);
        final int totalNumberOfEvents = 23;
        final EventCatchupCommand eventCatchupCommand = new EventCatchupCommand();

        final Duration catchupDuration = Duration.of(23, SECONDS);

        final CatchupCompletedForSubscriptionEvent catchupCompletedForSubscriptionEvent = new CatchupCompletedForSubscriptionEvent(
                commandId,
                subscriptionName,
                eventSourceName,
                componentName,
                eventCatchupCommand,
                catchupCompletedAt,
                totalNumberOfEvents
        );

        final CatchupInProgress catchupInProgress = mock(CatchupInProgress.class);

        when(catchupInProgress.getStartedAt()).thenReturn(catchupStartedAt);
        when(catchupStateManager.removeCatchupInProgress(subscriptionName, eventCatchupCommand)).thenReturn(catchupInProgress);
        when(catchupDurationCalculator.calculate(catchupStartedAt, catchupCompletedAt)).thenReturn(catchupDuration);

        when(catchupStateManager.noCatchupsInProgress(eventCatchupCommand)).thenReturn(false);

        catchupObserver.onCatchupCompleteForSubscription(catchupCompletedForSubscriptionEvent);

        verify(logger).info("CATCHUP for subscription 'mySubscription' completed at 2019-02-23T17:12:23Z");
        verify(logger).info("CATCHUP for subscription 'mySubscription' caught up 23 events");
        verify(logger).info("CATCHUP for subscription 'mySubscription' took 23000 milliseconds");

        verifyZeroInteractions(catchupProcessCompleter);
    }

    @Test
    public void shouldCompleteTheCatchupIfAllCatchupsForSubscriptionsComplete() throws Exception {

        final UUID commandId = randomUUID();
        final String subscriptionName = "mySubscription";
        final String eventSourceName = "myEventSource";
        final String componentName = "EVENT_LISTENER";
        final ZonedDateTime catchupCompletedAt = of(2019, 2, 23, 17, 12, 23, 0, UTC);
        final ZonedDateTime catchupStartedAt = catchupCompletedAt.minusSeconds(2);
        final int totalNumberOfEvents = 23;
        final EventCatchupCommand eventCatchupCommand = new EventCatchupCommand();

        final Duration catchupDuration = Duration.of(2_000, MILLIS);

        final CatchupCompletedForSubscriptionEvent catchupCompletedForSubscriptionEvent = new CatchupCompletedForSubscriptionEvent(
                commandId,
                subscriptionName,
                eventSourceName,
                componentName,
                eventCatchupCommand,
                catchupCompletedAt,
                totalNumberOfEvents
        );

        final CatchupInProgress catchupInProgress = mock(CatchupInProgress.class);

        when(catchupInProgress.getStartedAt()).thenReturn(catchupStartedAt);
        when(catchupStateManager.removeCatchupInProgress(subscriptionName, eventCatchupCommand)).thenReturn(catchupInProgress);
        when(catchupDurationCalculator.calculate(
                catchupStartedAt,
                catchupCompletedAt)).thenReturn(catchupDuration);

        when(catchupStateManager.noCatchupsInProgress(eventCatchupCommand)).thenReturn(true);

        catchupObserver.onCatchupCompleteForSubscription(catchupCompletedForSubscriptionEvent);

        verify(logger).info("CATCHUP for subscription 'mySubscription' completed at 2019-02-23T17:12:23Z");
        verify(logger).info("CATCHUP for subscription 'mySubscription' caught up 23 events");
        verify(logger).info("CATCHUP for subscription 'mySubscription' took 2000 milliseconds");

        verify(catchupProcessCompleter).handleCatchupComplete(commandId, eventCatchupCommand);
    }

    @Test
    public void shouldHandleCatchupProcessingOfEventFailed() throws Exception {

        final UUID commandId = randomUUID();
        final UUID eventId = randomUUID();
        final String metadata = "{some: metadata}";
        final NullPointerException exception = new NullPointerException("Ooops");
        final EventCatchupCommand eventCatchupCommand = new EventCatchupCommand();
        final String subscriptionName = "subscriptionName";

        final CatchupProcessingOfEventFailedEvent catchupProcessingOfEventFailedEvent = new CatchupProcessingOfEventFailedEvent(
                commandId,
                eventId,
                metadata,
                exception,
                eventCatchupCommand,
                subscriptionName
        );

        catchupObserver.onCatchupProcessingOfEventFailed(catchupProcessingOfEventFailedEvent);

        verify(catchupErrorStateManager).add(catchupErrorCaptor.capture(), eq(eventCatchupCommand));

        final CatchupError catchupError = catchupErrorCaptor.getValue();

        assertThat(catchupError.getEventId(), is(eventId));
        assertThat(catchupError.getException(), is(exception));
        assertThat(catchupError.getMetadata(), is(metadata));
        assertThat(catchupError.getCatchupCommand(), is(eventCatchupCommand));
        assertThat(catchupError.getSubscriptionName(), is(subscriptionName));
    }
}
