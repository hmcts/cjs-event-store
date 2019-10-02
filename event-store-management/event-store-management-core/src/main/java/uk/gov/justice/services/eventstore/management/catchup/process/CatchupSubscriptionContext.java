package uk.gov.justice.services.eventstore.management.catchup.process;

import uk.gov.justice.services.eventstore.management.events.catchup.CatchupType;
import uk.gov.justice.services.jmx.api.command.SystemCommand;
import uk.gov.justice.subscription.domain.subscriptiondescriptor.Subscription;

import java.util.Objects;
import java.util.UUID;

public class CatchupSubscriptionContext {

    private final UUID commandId;
    private final String componentName;
    private final Subscription subscription;
    private final CatchupType catchupType;
    private final SystemCommand systemCommand;

    public CatchupSubscriptionContext(
            final UUID commandId, final String componentName,
            final Subscription subscription,
            final CatchupType catchupType,
            final SystemCommand systemCommand) {
        this.commandId = commandId;
        this.componentName = componentName;
        this.subscription = subscription;
        this.catchupType = catchupType;
        this.systemCommand = systemCommand;
    }

    public UUID getCommandId() {
        return commandId;
    }

    public String getComponentName() {
        return componentName;
    }

    public Subscription getSubscription() {
        return subscription;
    }

    public CatchupType getCatchupType() {
        return catchupType;
    }

    public SystemCommand getSystemCommand() {
        return systemCommand;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof CatchupSubscriptionContext)) return false;
        final CatchupSubscriptionContext that = (CatchupSubscriptionContext) o;
        return Objects.equals(commandId, that.commandId) &&
                Objects.equals(componentName, that.componentName) &&
                Objects.equals(subscription, that.subscription) &&
                catchupType == that.catchupType &&
                Objects.equals(systemCommand, that.systemCommand);
    }

    @Override
    public int hashCode() {
        return Objects.hash(commandId, componentName, subscription, catchupType, systemCommand);
    }

    @Override
    public String toString() {
        return "CatchupSubscriptionContext{" +
                "commandId=" + commandId +
                ", componentName='" + componentName + '\'' +
                ", subscription=" + subscription +
                ", catchupType=" + catchupType +
                ", systemCommand=" + systemCommand +
                '}';
    }
}
