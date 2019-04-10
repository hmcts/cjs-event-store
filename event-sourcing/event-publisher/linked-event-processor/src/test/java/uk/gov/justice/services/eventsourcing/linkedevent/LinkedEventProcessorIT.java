package uk.gov.justice.services.eventsourcing.linkedevent;

import static java.nio.file.Paths.get;
import static java.util.Optional.of;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static uk.gov.justice.services.test.utils.core.reflection.ReflectionUtil.setField;

import uk.gov.justice.services.common.converter.StringToJsonObjectConverter;
import uk.gov.justice.services.eventsourcing.EventFetcherRepository;
import uk.gov.justice.services.eventsourcing.linkedevent.helpers.EventFactory;
import uk.gov.justice.services.eventsourcing.linkedevent.helpers.TestEventInserter;
import uk.gov.justice.services.eventsourcing.prepublish.LinkedEventFactory;
import uk.gov.justice.services.eventsourcing.prepublish.MetadataEventNumberUpdater;
import uk.gov.justice.services.eventsourcing.prepublish.PrePublishRepository;
import uk.gov.justice.services.eventsourcing.repository.jdbc.AnsiSQLEventLogInsertionStrategy;
import uk.gov.justice.services.eventsourcing.repository.jdbc.event.Event;
import uk.gov.justice.services.eventsourcing.repository.jdbc.event.EventConverter;
import uk.gov.justice.services.eventsourcing.repository.jdbc.event.EventJdbcRepositoryFactory;
import uk.gov.justice.services.eventsourcing.repository.jdbc.event.LinkedEvent;
import uk.gov.justice.services.eventsourcing.repository.jdbc.event.LinkedEventJdbcRepository;
import uk.gov.justice.services.eventsourcing.repository.jdbc.eventstream.EventStreamJdbcRepositoryFactory;
import uk.gov.justice.services.jdbc.persistence.JdbcRepositoryHelper;
import uk.gov.justice.services.test.utils.core.eventsource.EventStoreInitializer;
import uk.gov.justice.services.test.utils.persistence.FrameworkTestDataSourceFactory;
import uk.gov.justice.services.test.utils.persistence.TestJdbcDataSourceProvider;
import uk.gov.justice.subscription.domain.eventsource.EventSourceDefinition;
import uk.gov.justice.subscription.domain.eventsource.Location;
import uk.gov.justice.subscription.registry.EventSourceDefinitionRegistry;
import uk.gov.justice.subscription.registry.SubscriptionDataSourceProvider;

import java.net.MalformedURLException;
import java.net.URL;
import java.sql.Connection;
import java.util.Optional;

import javax.sql.DataSource;

import org.junit.Before;
import org.junit.Test;

public class LinkedEventProcessorIT {

    private final DataSource eventStoreDataSource = new FrameworkTestDataSourceFactory().createEventStoreDataSource();
    private final TestEventInserter testEventInserter = new TestEventInserter();
    private final EventFactory eventFactory = new EventFactory();
    private final LinkedEventProcessor linkedEventProcessor = new LinkedEventProcessor();
    private final EventFetcherRepository eventFetcherRepository = new EventFetcherRepository();
    private final EventConverter eventConverter = new EventConverter();
    private final EventStreamJdbcRepositoryFactory eventStreamJdbcRepositoryFactory = new EventStreamJdbcRepositoryFactory();
    private final EventJdbcRepositoryFactory eventJdbcRepositoryFactory = new EventJdbcRepositoryFactory();

    @Before
    public void initDatabase() throws Exception {
        new EventStoreInitializer().initializeEventStore(eventStoreDataSource);
        setUpLinkedEventProcessor();
     }

    private URL getFromClasspath(final String name) throws MalformedURLException {
        return get(getClass().getClassLoader().getResource(name).getPath()).toUri().toURL();
    }

    private SubscriptionDataSourceProvider setUpSubscriptionDataSourceProvider() throws MalformedURLException {
        final SubscriptionDataSourceProvider subscriptionDataSourceProvider = new SubscriptionDataSourceProvider();
        final TestJdbcDataSourceProvider testJdbcDataSourceProvider = new TestJdbcDataSourceProvider();
        testJdbcDataSourceProvider.setDataSource(eventStoreDataSource);
        setField(subscriptionDataSourceProvider, "jdbcDataSourceProvider", testJdbcDataSourceProvider);
        final EventSourceDefinitionRegistry eventSourceDefinitionRegistry = new EventSourceDefinitionRegistry();
        final URL url = getFromClasspath("yaml/event-sources.yaml");
        final Location location = new Location(null, null, of(url.toString()));
        eventSourceDefinitionRegistry.register(new EventSourceDefinition("", true, location));
        setField(subscriptionDataSourceProvider, "eventSourceDefinitionRegistry", eventSourceDefinitionRegistry);

        setField(eventJdbcRepositoryFactory, "eventInsertionStrategy", new AnsiSQLEventLogInsertionStrategy());
        setField(eventJdbcRepositoryFactory, "jdbcRepositoryHelper", new JdbcRepositoryHelper());
        setField(eventJdbcRepositoryFactory, "jdbcDataSourceProvider", testJdbcDataSourceProvider);

        setField(eventStreamJdbcRepositoryFactory, "eventStreamJdbcRepositoryHelper", new JdbcRepositoryHelper());
        setField(eventStreamJdbcRepositoryFactory, "jdbcDataSourceProvider", testJdbcDataSourceProvider);


        return subscriptionDataSourceProvider;
    }

    private void setUpLinkedEventProcessor() throws MalformedURLException {
        setField(linkedEventProcessor, "metadataEventNumberUpdater", new MetadataEventNumberUpdater());
        setField(linkedEventProcessor, "eventConverter", eventConverter);
        setField(linkedEventProcessor, "prePublishRepository", new PrePublishRepository());
        setField(linkedEventProcessor, "linkedEventFactory", new LinkedEventFactory());
        setField(linkedEventProcessor, "linkedEventJdbcRepository", new LinkedEventJdbcRepository());
        setField(linkedEventProcessor, "subscriptionDataSourceProvider", setUpSubscriptionDataSourceProvider());

        setField(eventConverter, "stringToJsonObjectConverter", new StringToJsonObjectConverter());
    }

    @Test
    public void shouldFetchAnEventById() throws Exception {

        final String name = "example.first-event";
        final long sequenceId = 1L;
        final long eventNumber = 1L;
        final Event event = eventFactory.createEvent(name, sequenceId, eventNumber);

        testEventInserter.insertIntoEventLog(event);
        linkedEventProcessor.createLinkedEvent(event);
        try (final Connection connection = eventStoreDataSource.getConnection()) {

            final Optional<LinkedEvent> linkedEventOptional = eventFetcherRepository.getLinkedEvent(event.getId(), connection);

            if (linkedEventOptional.isPresent()) {
                final LinkedEvent actual = linkedEventOptional.get();
                assertThat(actual.getId(), is(event.getId()));
                assertThat(actual.getName(), is(name));
                assertThat(actual.getPreviousEventNumber(), is(0L));
                assertThat(actual.getSequenceId(), is(sequenceId));

            } else {
                fail();
            }
        }
    }
}
