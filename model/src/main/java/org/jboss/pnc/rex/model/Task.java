package org.jboss.pnc.rex.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.Singular;
import lombok.ToString;
import org.infinispan.protostream.annotations.ProtoDoc;
import org.infinispan.protostream.annotations.ProtoFactory;
import org.infinispan.protostream.annotations.ProtoField;
import org.jboss.pnc.rex.common.enums.Mode;
import org.jboss.pnc.rex.common.enums.State;
import org.jboss.pnc.rex.common.enums.StopFlag;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Task is an entity that holds data of remotely executed process.
 * <p>
 * TaskController manipulates Task's data.
 * <p>
 * Service has to be installed through BatchTaskInstaller that is provided by TaskTarget. After installation, Task's
 * data is held by Infinispan cache inside TaskRegistry/TaskContainer.
 *
 * @author Jan Michalov <jmichalo@redhat.com>
 */
@Setter
@ToString
@Builder(toBuilder = true)
@ProtoDoc("@Indexed")
@AllArgsConstructor(onConstructor_ = {@ProtoFactory})
public class Task {
    /**
     * Uniquely identifies a Task and serves as a key in Infinispan cache.
     */
    @Getter(onMethod_ = {@ProtoField(number = 1)})
    private final String name;

    /**
     * Definition of a request to remote entity for starting a Task
     */
    @Getter(onMethod_ = @ProtoField(number = 2))
    private Request remoteStart;

    /**
     * Definition of a request to remote entity for cancelling a Task
     */
    @Getter(onMethod_ = @ProtoField(number = 3))
    private Request remoteCancel;

    /**
     * Definition of a request to the initial caller which is used for transition notifications
     */
    @Getter(onMethod_ = @ProtoField(number = 4))
    private Request callerNotifications;

    /**
     * TaskController mode.
     */
    @Getter(onMethod_ = @ProtoField(number = 5))
    private Mode controllerMode;

    /**
     * Current state of a task. Default is State.IDLE.
     */
    @Getter(onMethod_ = {@ProtoField(number = 6), @ProtoDoc("@Field")})
    private State state;

    /**
     * Tasks that are dependent on this Task.
     * <p>
     * Parents of this Task.
     */
    @Singular
    @Getter(onMethod_ = @ProtoField(number = 7))
    private Set<String> dependants = new HashSet<>();

    /**
     * Number of unfinishedDependencies. Task can't remotely start if the number is positive.
     */
    @Getter(onMethod_ = {@ProtoField(number = 8, defaultValue = "-1")})
    private int unfinishedDependencies;

    /**
     * Tasks that this Task depends on.
     * <p>
     * Children of this Task.
     */
    @Singular
    @Getter(onMethod_ = @ProtoField(number = 9))
    private Set<String> dependencies = new HashSet<>();

    /**
     * Flag which signifies a reason why the Task stopped execution.
     */
    @Getter(onMethod_ = @ProtoField(number = 10))
    private StopFlag stopFlag;

    /**
     * List of all responses(bodies) received from remote entity.
     */
    @Singular
    @Getter(onMethod_ = @ProtoField(number = 11))
    private List<ServerResponse> serverResponses = new ArrayList<>();

    /**
     * This flag indicates whether task should be dropped from queue and start remote execution.
     */
    @Getter(onMethod_ = @ProtoField(number = 12, defaultValue = "false"))
    private Boolean starting;

    public void incUnfinishedDependencies() {
        unfinishedDependencies++;
    }

    public void decUnfinishedDependencies() {
        unfinishedDependencies--;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Task task = (Task) o;
        return name.equals(task.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }
}
