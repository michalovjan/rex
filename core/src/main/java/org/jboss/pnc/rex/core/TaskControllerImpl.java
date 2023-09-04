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
package org.jboss.pnc.rex.core;

import io.quarkus.narayana.jta.TransactionSemantics;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.infinispan.client.hotrod.VersionedValue;
import org.jboss.pnc.rex.common.enums.Mode;
import org.jboss.pnc.rex.common.enums.State;
import org.jboss.pnc.rex.common.enums.StopFlag;
import org.jboss.pnc.rex.common.enums.Transition;
import org.jboss.pnc.rex.core.api.DependencyMessenger;
import org.jboss.pnc.rex.core.api.DependentMessenger;
import org.jboss.pnc.rex.core.api.TaskController;
import org.jboss.pnc.rex.common.exceptions.ConcurrentUpdateException;
import org.jboss.pnc.rex.core.jobs.ChainingJob;
import org.jboss.pnc.rex.core.jobs.DecreaseCounterJob;
import org.jboss.pnc.rex.core.jobs.DelegateJob;
import org.jboss.pnc.rex.core.jobs.DeleteTaskJob;
import org.jboss.pnc.rex.core.jobs.DependantDeletedJob;
import org.jboss.pnc.rex.core.jobs.InvokeStartJob;
import org.jboss.pnc.rex.core.jobs.InvokeStopJob;
import org.jboss.pnc.rex.core.jobs.ControllerJob;
import org.jboss.pnc.rex.core.jobs.DependencyCancelledJob;
import org.jboss.pnc.rex.core.jobs.DependencyStoppedJob;
import org.jboss.pnc.rex.core.jobs.DependencySucceededJob;
import org.jboss.pnc.rex.core.jobs.MarkForCleaningJob;
import org.jboss.pnc.rex.core.jobs.NotifyCallerJob;
import org.jboss.pnc.rex.core.jobs.PokeCleanJob;
import org.jboss.pnc.rex.core.jobs.PokeQueueJob;
import org.jboss.pnc.rex.model.ServerResponse;
import org.jboss.pnc.rex.model.Task;
import org.jboss.pnc.rex.model.TransitionTime;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Event;
import javax.transaction.Transactional;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;

import static javax.enterprise.event.TransactionPhase.BEFORE_COMPLETION;
import static javax.enterprise.event.TransactionPhase.IN_PROGRESS;
import static javax.transaction.Transactional.TxType.MANDATORY;

@Slf4j
@ApplicationScoped
public class TaskControllerImpl implements TaskController, DependentMessenger, DependencyMessenger {

    private final TaskContainerImpl container;

    private final Event<ControllerJob> scheduleJob;

    private final boolean cleanTasks;

    public TaskControllerImpl(TaskContainerImpl container,
                              Event<ControllerJob> scheduleJob,
                              @ConfigProperty(
                                      name = "scheduler.options.task-configuration.clean",
                                      defaultValue = "true") boolean cleanTasks) {
        this.container = container;
        this.scheduleJob = scheduleJob;
        this.cleanTasks = cleanTasks;
    }

    private List<ControllerJob> transition(Task task) {
        Transition transition = getTransition(task);
        if (transition != null) {
            log.info("TRANSITION {}: before: {} after: {}", task.getName(), transition.getBefore().toString(), transition.getAfter().toString());
            task.getTimestamps().add(new TransitionTime(transition, Instant.now()));
        }

        List<ControllerJob> tasks = new ArrayList<>();

        if (transition == null) {
            return tasks;
        }

        tasks.addAll(switch (transition) {
            //no tasks
            case NEW_to_WAITING, NEW_to_ENQUEUED, WAITING_to_ENQUEUED -> List.of();

            case ENQUEUED_to_STARTING -> List.of(new InvokeStartJob(task));

            case UP_to_STOP_REQUESTED, STARTING_to_STOP_REQUESTED -> List.of(new InvokeStopJob(task));

            case STOPPING_TO_STOPPED -> List.of(new DependencyCancelledJob(task), new DecreaseCounterJob(task));

            case NEW_to_STOPPED, WAITING_to_STOPPED, ENQUEUED_to_STOPPED -> {
                var jobs = new ArrayList<ControllerJob>();
                switch (task.getStopFlag()) {
                    case CANCELLED -> jobs.add(new DependencyCancelledJob(task));
                    case DEPENDENCY_FAILED -> jobs.add(new DependencyStoppedJob(task));
                    // UNSUCCESSFUL is in FAILED transitions
                    case UNSUCCESSFUL, NONE -> {}
                }
                yield jobs;
            }

            case UP_to_FAILED, STARTING_to_START_FAILED, STOPPING_TO_STOP_FAILED, STOP_REQUESTED_to_STOP_FAILED
                    -> List.of(new DependencyStoppedJob(task), new DecreaseCounterJob(task));

            //no tasks
            case STOP_REQUESTED_to_STOPPING, STARTING_to_UP -> List.of();

            case UP_to_SUCCESSFUL -> List.of(new DependencySucceededJob(task), new DecreaseCounterJob(task));
        });

        // add common tasks on transitioning into a specific StateGroup
        tasks.addAll(switch (transition.getAfter().getGroup()) {
            case IDLE,QUEUED, RUNNING -> List.of();
            case FINAL -> {
                var jobs = new ArrayList<ControllerJob>();
                jobs.add(new PokeQueueJob());
                if (shouldMarkImmediately(task, transition))
                    jobs.add(new MarkForCleaningJob(task));
                if (shouldDeleteImmediately(task, transition))
                    jobs.add(moveToTheEndOfTransaction(new DeleteTaskJob(task)));

                yield jobs;
            }
        });

        task.setState(transition.getAfter());

        // notify the caller about a transition
        var notifyJob = new NotifyCallerJob(transition, task);
        if (shouldMarkAfterNotification(task, transition)) {
            // send notification and delete jobs after a success
            tasks.add(ChainingJob.of(notifyJob).chainOnSuccess(withTransactionAndTolerance(new MarkForCleaningJob(task, true))));
        } else {
            // send notification
            tasks.add(notifyJob);
        }

        log.info("SCHEDULE {}: {}", task.getName(), tasks);
        return tasks;
    }

    private DelegateJob moveToTheEndOfTransaction(ControllerJob delegate) {
        return DelegateJob.builder()
                .async(false)
                .invocationPhase(BEFORE_COMPLETION)
                .tolerant(false)
                .transactional(true)
                .transactionSemantics(TransactionSemantics.JOIN_EXISTING)
                .context(delegate.getContext().orElse(null))
                .delegate(delegate)
                .build();
    }
    private DelegateJob withTransactionAndTolerance(ControllerJob delegate) {
        return DelegateJob.builder()
                .async(false)
                .invocationPhase(IN_PROGRESS)
                .tolerant(true)
                .transactional(true)
                .transactionSemantics(TransactionSemantics.REQUIRE_NEW)
                .context(delegate.getContext().orElse(null))
                .delegate(delegate)
                .build();
    }

    private Transition getTransition(Task task) {
        return switch (task.getState()) {
            case NEW -> {
                if (shouldStop(task))
                    yield Transition.NEW_to_STOPPED;
                if (shouldQueue(task))
                    yield Transition.NEW_to_ENQUEUED;
                if (shouldWait(task))
                    yield Transition.NEW_to_WAITING;
                yield null;
            }
            case WAITING -> {
                if (shouldStop(task))
                    yield Transition.WAITING_to_STOPPED;
                if (shouldQueue(task))
                    yield Transition.WAITING_to_ENQUEUED;
                yield null;
            }
            case ENQUEUED -> {
                if (shouldStop(task))
                    yield Transition.ENQUEUED_to_STOPPED;
                if (shouldStart(task))
                    yield Transition.ENQUEUED_to_STARTING;
                yield null;
            }
            case STARTING -> {
                if (task.getStopFlag() == StopFlag.CANCELLED)
                    yield Transition.STARTING_to_STOP_REQUESTED;
                List<ServerResponse> responses = task.getServerResponses().stream().filter(sr -> sr.getState() == State.STARTING).toList();
                if (responses.stream().anyMatch(ServerResponse::isPositive))
                    yield Transition.STARTING_to_UP;
                if (responses.stream().anyMatch(ServerResponse::isNegative))
                    yield Transition.STARTING_to_START_FAILED;
                yield null;
            }
            case UP -> {
                if (task.getStopFlag() == StopFlag.CANCELLED)
                    yield Transition.UP_to_STOP_REQUESTED;
                List<ServerResponse> responses = task.getServerResponses().stream().filter(sr -> sr.getState() == State.UP).toList();
                if (responses.stream().anyMatch(ServerResponse::isPositive))
                    yield Transition.UP_to_SUCCESSFUL;
                if (responses.stream().anyMatch(ServerResponse::isNegative))
                    yield Transition.UP_to_FAILED;
                yield null;
            }
            case STOP_REQUESTED -> {
                List<ServerResponse> responses = task.getServerResponses().stream().filter(sr -> sr.getState() == State.STOP_REQUESTED).toList();
                if (responses.stream().anyMatch(ServerResponse::isPositive))
                    yield Transition.STOP_REQUESTED_to_STOPPING;
                if (responses.stream().anyMatch(ServerResponse::isNegative))
                    yield Transition.STOP_REQUESTED_to_STOP_FAILED;
                yield null;
            }
            case STOPPING -> {
                List<ServerResponse> responses = task.getServerResponses().stream().filter(sr -> sr.getState() == State.STOPPING).toList();
                if (responses.stream().anyMatch(ServerResponse::isPositive))
                    yield Transition.STOPPING_TO_STOPPED;
                if (responses.stream().anyMatch(ServerResponse::isNegative))
                    yield Transition.STOPPING_TO_STOP_FAILED;
                yield null;
            }
            // final states have no possible transitions
            case START_FAILED, STOP_FAILED, FAILED, SUCCESSFUL, STOPPED -> null;
        };
    }

    private static boolean shouldWait(Task task) {
        return task.getControllerMode() == Mode.ACTIVE && task.getUnfinishedDependencies() > 0;
    }

    private boolean shouldQueue(Task task) {
        return task.getControllerMode() == Mode.ACTIVE && task.getUnfinishedDependencies() <= 0;
    }

    private boolean shouldStop(Task task) {
        return task.getStopFlag() != StopFlag.NONE;
    }

    private boolean shouldStart(Task task) {
        return task.getStarting();
    }

    private boolean shouldDeleteImmediately(Task task, Transition transition) {
        return shouldDelete(task, transition) && task.getCallerNotifications() == null;
    }

    private boolean shouldDelete(Task task, Transition transition) {
        return cleanTasks && task.getDependants().isEmpty() && transition.getAfter().isFinal();
    }

    private boolean shouldMarkAfterNotification(Task task, Transition transition) {
        return shouldMark(task, transition) && task.getCallerNotifications() != null;
    }

    private boolean shouldMark(Task task, Transition transition) {
        return cleanTasks && transition.getAfter().isFinal();
    }

    /**
     * A Task gets marked for deletion either immediately upon transitioning into a final state or after a FINAL
     * NOTIFICATION completes.
     */
    private boolean shouldMarkImmediately(Task task, Transition transition) {
        return cleanTasks && transition.getAfter().isFinal() && task.getCallerNotifications() == null;
    }

    private void handle(VersionedValue<Task> taskMetadata, Task task) {
        handle(taskMetadata, task, null);
    }

    private void handle(VersionedValue<Task> taskMetadata, Task task, ControllerJob[] forcedJobs) {
        List<ControllerJob> jobs = transition(task);
        if (forcedJobs != null && forcedJobs.length != 0) {
            jobs.addAll(Arrays.asList(forcedJobs));
        }

        saveChanges(taskMetadata, task);

        doExecute(jobs);
    }

    private void saveChanges(VersionedValue<Task> taskMetadata, Task task) {
        log.debug("SAVE {}: Saving task into ISPN. (ISPN-VERSION: {}) BODY: {}",
                task.getName(),
                taskMetadata.getVersion(),
                task);

        boolean pushed = container.getCache().replaceWithVersion(task.getName(), task, taskMetadata.getVersion());
        if (!pushed) {
            log.error("SAVE {}: Concurrent update detected. Transaction will fail.", task.getName());
            throw new ConcurrentUpdateException("Task " + task.getName() + " was remotely updated during the transaction");
        }
    }

    private void doExecute(List<ControllerJob> jobs) {
        for (ControllerJob job : jobs) {
            scheduleJob.fire(job);
        }
    }

    @Override
    @Transactional(MANDATORY)
    public void setMode(String name, Mode mode) {
        setMode(name, mode, false);
    }

    @Override
    @Transactional(MANDATORY)
    public void setMode(String name, Mode mode, boolean pokeQueue) {
        // #1 PULL
        VersionedValue<Task> taskMetadata = container.getRequiredTaskWithMetadata(name);
        Task task = taskMetadata.getValue();

        // #2 ALTER
        Mode currentMode = task.getControllerMode();
        if (currentMode == Mode.CANCEL || (mode == Mode.IDLE && currentMode == Mode.ACTIVE)) {
            //no possible movement
            log.error("SET-MODE {}: Incorrect request. (current-mode: {}, proposed-mode: {}) ",
                    name,
                    currentMode,
                    mode);
            return;
        }
        task.setControllerMode(mode);
        if (mode == Mode.CANCEL) {
            task.setStopFlag(StopFlag.CANCELLED);
        }

        // #3 HANDLE
        if (pokeQueue) {
            handle(taskMetadata, task, new ControllerJob[]{new PokeQueueJob()});
        } else {
            handle(taskMetadata, task);
        }
    }

    @Override
    @Transactional(MANDATORY)
    public void accept(String name, Object response) {
        // #1 PULL
        VersionedValue<Task> taskMetadata = container.getRequiredTaskWithMetadata(name);
        Task task = taskMetadata.getValue();

        // #2 ALTER
        if (EnumSet.of(State.STARTING, State.UP, State.STOP_REQUESTED, State.STOPPING).contains(task.getState())) {
            ServerResponse positiveResponse = new ServerResponse(task.getState(), true, response);
            List<ServerResponse> responses = task.getServerResponses();
            responses.add(positiveResponse);
        } else {
            var exception = new IllegalStateException("Got response from the remote entity while not in a state to do so. Task: " + task.getName() + " State: " + task.getState());
            log.error("ERROR: ", exception);
            throw exception;
        }

        // #3 HANDLE
        handle(taskMetadata, task);
    }

    @Override
    @Transactional(MANDATORY)
    public void fail(String name, Object response) {
        // #1 PULL
        VersionedValue<Task> taskMetadata = container.getRequiredTaskWithMetadata(name);
        Task task = taskMetadata.getValue();

        // #2 ALTER
        if (EnumSet.of(State.STARTING, State.UP, State.STOP_REQUESTED, State.STOPPING).contains(task.getState())){
            ServerResponse negativeResponse = new ServerResponse(task.getState(), false, response);
            List<ServerResponse> responses = task.getServerResponses();
            responses.add(negativeResponse);
            task.setServerResponses(responses); //probably unnecessary
            //maybe assert it was NONE before
            task.setStopFlag(StopFlag.UNSUCCESSFUL);
        } else {
            throw new IllegalStateException("Got response from the remote entity while not in a state to do so. Task: " + task.getName() + " State: " + task.getState());
        }

        // #3 HANDLE
        handle(taskMetadata, task);
    }

    @Override
    @Transactional(MANDATORY)
    public void dequeue(String name) {
        // #1 PULL
        VersionedValue<Task> taskMetadata = container.getRequiredTaskWithMetadata(name);
        Task task = taskMetadata.getValue();

        // #2 ALTER
        if (task.getState() == State.ENQUEUED) {
            task.setStarting(true);
        } else {
            throw new IllegalStateException("Attempting to dequeue while not in a state to do. Task: " + task.getName() + " State: " + task.getState());
        }

        // #3 HANDLE
        handle(taskMetadata, task);
    }

    @Override
    @Transactional(MANDATORY)
    public void dependencySucceeded(String name) {
        // #1 PULL
        VersionedValue<Task> taskMetadata = container.getWithMetadata(name);
        if (taskMetadata == null) {
            throw new ConcurrentUpdateException("Task missing in critical moment. This could happen with concurrent deletion of this Task. Task: " + name);
        }
        Task task = taskMetadata.getValue();

        // #2 ALTER
        task.decUnfinishedDependencies();

        // #3 HANDLE
        handle(taskMetadata, task);
    }

    @Override
    @Transactional(MANDATORY)
    public void dependencyStopped(String name) {
        // #1 PULL
        VersionedValue<Task> taskMetadata = container.getWithMetadata(name);
        if (taskMetadata == null) {
            throw new ConcurrentUpdateException("Task missing in critical moment. This could happen with concurrent deletion of this Task. Task: " + name);
        }
        Task task = taskMetadata.getValue();

        // #2 ALTER
        //maybe assert it was NONE before
        task.setStopFlag(StopFlag.DEPENDENCY_FAILED);

        // #3 HANDLE
        handle(taskMetadata, task);
    }


    @Override
    @Transactional(MANDATORY)
    public void dependencyCancelled(String name) {
        // #1 PULL
        VersionedValue<Task> taskMetadata = container.getWithMetadata(name);
        if (taskMetadata == null) {
            throw new ConcurrentUpdateException("Task missing in critical moment. This could happen with concurrent deletion of this Task. Task: " + name);
        }
        Task task = taskMetadata.getValue();

        // #2 ALTER
        //maybe assert it was NONE before
        task.setStopFlag(StopFlag.CANCELLED);

        // #3 HANDLE
        handle(taskMetadata, task);
    }

    @Override
    @Transactional(MANDATORY)
    public void dependantDeleted(String name, String deletedDependant) {
        // #1 PULL
        VersionedValue<Task> taskMetadata = container.getWithMetadata(name);
        if (taskMetadata == null) {
            // Task could have been already deleted DependantDeletedJob in the same transaction
            return;
        }
        Task task = taskMetadata.getValue();

        if (!task.getState().isFinal() || !task.isDisposable()) {
            log.info("TASK {}: Not in final state, removing deleted dependant {} from the task.", name, deletedDependant);
            //REMOVE dependant so it doesn't get referenced later
            task.getDependants().remove(deletedDependant);

            saveChanges(taskMetadata, task);
        } else {
            // DELETE
            doExecute(List.of(new DeleteTaskJob(task)));
        }

    }

    @Override
    @Transactional(MANDATORY)
    public void markForDisposal(String name, boolean pokeCleaner) {
        // #1 PULL
        VersionedValue<Task> taskMetadata = container.getRequiredTaskWithMetadata(name);
        Task task = taskMetadata.getValue();

        // #2 ALTER
        if (!task.getState().isFinal()) {
            throw new IllegalStateException("Attempting to mark a task for disposal while not in a final state. Task: " + task.getName() + " State: " + task.getState());
        }

        log.info("TASK {}: MARKING FOR DELETION.", name);
        task.setDisposable(true);

        // once marked for removal, we can allow scheduling tasks with same constraints
        handleOptionalConstraint(task);

        saveChanges(taskMetadata, task);

        // #3 HANDLE (there is no transition)
        if (pokeCleaner) {
            doExecute(List.of(new PokeCleanJob(task)));
        }
    }

    @Override
    @Transactional(MANDATORY)
    public void delete(String name) {
        // #1 PULL
        VersionedValue<Task> taskMetadata = container.getRequiredTaskWithMetadata(name);
        Task task = taskMetadata.getValue();

        // #2 DELETE
        if (!task.getState().isFinal()) {
            throw new IllegalStateException("Attempting to delete a task while not in a final state. Task: " + task.getName() + " State: " + task.getState());
        }

        if (!task.isDisposable()) {
            throw new IllegalStateException("Attempting to delete a task while not marked as disposable. Task: " + task.getName() + " State: " + task.getState());
        }

        log.debug("DELETE {}: Deleting task from ISPN. (ISPN-VERSION: {}) BODY: {}",
                task.getName(),
                taskMetadata.getVersion(),
                task);

        boolean deleted = container.getCache().removeWithVersion(name, taskMetadata.getVersion());
        if (!deleted) {
            log.error("DELETE {}: Concurrent update detected. Transaction will fail.", task.getName());
            throw new ConcurrentUpdateException("Task " + task.getName() + " was remotely updated during the transaction");
        }

        handleOptionalConstraint(task);

        // #3 HANDLE (there is no transition) and CASCADE
        doExecute(List.of(new DependantDeletedJob(task)));
    }

    private void handleOptionalConstraint(Task task) {
        String constraint = task.getConstraint();
        if (constraint != null) {
            VersionedValue<String> constraintMeta = container.getConstraintCache().getWithMetadata(constraint);
            if (constraintMeta != null) {
                log.debug("TASK {}: Removing constraint '{}' from cache.", task.getName(), constraint);
                container.getConstraintCache().removeWithVersion(constraint, constraintMeta.getVersion());
            }
        }
    }
}