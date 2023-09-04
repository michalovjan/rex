/**
 * JBoss, Home of Professional Open Source.
 * Copyright 2021-2021 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Task is an entity that holds data of remotely executed process.
 * <p>
 * TaskController manipulates Task's data.
 * <p>
 * Task has to be installed through BatchTaskInstaller that is provided by TaskTarget. After installation, Task's
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
     * Second unique constraint alongside Task.name
     */
    @Getter(onMethod_ = {@ProtoField(number = 2)})
    private final String constraint;

    /*
     * Correlation ID between tasks that were triggered at the same time.
     */
    @Getter(onMethod_ = {@ProtoField(number = 3), @ProtoDoc("@Field(index=Index.YES)")})
    private final String correlationID;

    /**
     * Definition of a request to remote entity for starting a Task
     */
    @Getter(onMethod_ = @ProtoField(number = 4))
    private Request remoteStart;

    /**
     * Definition of a request to remote entity for cancelling a Task
     */
    @Getter(onMethod_ = @ProtoField(number = 5))
    private Request remoteCancel;

    /**
     * Definition of a request to the initial caller which is used for transition notifications
     */
    @Getter(onMethod_ = @ProtoField(number = 6))
    private Request callerNotifications;

    /**
     * TaskController mode.
     */
    @Getter(onMethod_ = @ProtoField(number = 7))
    private Mode controllerMode;

    /**
     * Current state of a task. Default is State.IDLE.
     */
    @Getter(onMethod_ = {@ProtoField(number = 8), @ProtoDoc("@Field(index=Index.YES)")})
    private State state;

    /**
     * Tasks that are dependent on this Task.
     * <p>
     * Parents of this Task.
     */
    @Singular
    @Getter(onMethod_ = @ProtoField(number = 9))
    private Set<String> dependants;

    /**
     * Number of unfinishedDependencies. Task can't remotely start if the number is positive.
     */
    @Getter(onMethod_ = {@ProtoField(number = 10, defaultValue = "-1")})
    private int unfinishedDependencies;

    /**
     * Tasks that this Task depends on.
     * <p>
     * Children of this Task.
     */
    @Singular
    @Getter(onMethod_ = @ProtoField(number = 11))
    private Set<String> dependencies;

    /**
     * Flag which signifies a reason why the Task stopped execution.
     */
    @Getter(onMethod_ = @ProtoField(number = 12))
    private StopFlag stopFlag;

    /**
     * List of all responses(bodies) received from remote entity.
     */
    @Singular
    @Getter(onMethod_ = @ProtoField(number = 13, collectionImplementation = ArrayList.class))
    private List<ServerResponse> serverResponses;

    /**
     * This flag indicates whether task should be dropped from queue and start remote execution.
     */
    @Getter(onMethod_ = @ProtoField(number = 14, defaultValue = "false"))
    private Boolean starting;

    @Getter(onMethod_ = @ProtoField(number = 15))
    private Configuration configuration;

    /**
     * Set of ORDERED timestamps of transitions by time (used TreeSet)
     *
     * INFINISPAN caveat: Infinispan doesn't serialize Maps, therefore Set is used
     */
    @Getter(onMethod_ = @ProtoField(number = 16, collectionImplementation = TreeSet.class))
    private Set<TransitionTime> timestamps;

    /**
     * This flag indicates that this task can be removed from ISPN Cache. Usually a Task flagged to be removed after a
     * Notification completes or, in case no Notifications, immediately after transitioning into a finished State.
     *
     * Even though a Task can be flagged disposable, it won't be removed until all dependants are removed beforehand.
     */
    @Getter(onMethod_ = @ProtoField(number = 17, defaultValue = "false"))
    private boolean disposable;

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
