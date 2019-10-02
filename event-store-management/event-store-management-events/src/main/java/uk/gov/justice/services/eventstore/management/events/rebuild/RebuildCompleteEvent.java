package uk.gov.justice.services.eventstore.management.events.rebuild;

import uk.gov.justice.services.jmx.api.command.SystemCommand;

import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.UUID;

public class RebuildCompleteEvent {

    private final UUID commandId;
    private final SystemCommand target;
    private final ZonedDateTime rebuildCompletedAt;

    public RebuildCompleteEvent(
            final UUID commandId,
            final SystemCommand target,
            final ZonedDateTime rebuildCompletedAt) {
        this.commandId = commandId;
        this.target = target;
        this.rebuildCompletedAt = rebuildCompletedAt;
    }

    public UUID getCommandId() {
        return commandId;
    }

    public SystemCommand getTarget() {
        return target;
    }

    public ZonedDateTime getRebuildCompletedAt() {
        return rebuildCompletedAt;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof RebuildCompleteEvent)) return false;
        final RebuildCompleteEvent that = (RebuildCompleteEvent) o;
        return Objects.equals(commandId, that.commandId) &&
                Objects.equals(target, that.target) &&
                Objects.equals(rebuildCompletedAt, that.rebuildCompletedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(commandId, target, rebuildCompletedAt);
    }

    @Override
    public String toString() {
        return "RebuildCompleteEvent{" +
                "commandId=" + commandId +
                ", target=" + target +
                ", rebuildCompletedAt=" + rebuildCompletedAt +
                '}';
    }
}
