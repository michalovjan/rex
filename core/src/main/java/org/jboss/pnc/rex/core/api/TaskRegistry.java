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
package org.jboss.pnc.rex.core.api;

import org.jboss.pnc.rex.common.exceptions.TaskMissingException;
import org.jboss.pnc.rex.model.Task;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * The registry is used to retrieve Tasks.
 *
 * @author Jan Michalov <jmichalo@redhat.com>
 */
public interface TaskRegistry {
    /**
     * Returns the Task for a unique ServiceName.
     *
     * @param task the serviceName of the service
     * @return the task or null if doesn't exist
     */
    Task getTask(String task);

    /**
     * Returns the Task for a unique ServiceName. Throws an exception if not found.
     *
     * @param task the task name
     * @return the task
     * @throws TaskMissingException the task was not found
     */
    Task getRequiredTask(String task) throws TaskMissingException;

    /**
     * Returns all Tasks present in the cache filtered by parameters
     *
     * (Can be costly without filters)
     *
     * @param waiting is in StateGroup.IDLE state
     * @param running is in StateGroup.RUNNING state
     * @param finished is in StateGroup.FINAL state
     * @return list of filtered services
     */
    List<Task> getTasks(boolean waiting, boolean running, boolean finished);


    /**
     * Get the task results of the direct dependencies of the task, irrespective of the task configuration to allow
     * or deny it
     *
     * @param task task to return task results
     * @return Map of taskName and the result
     */
    Map<String, Object> getTaskResults(Task task);

    List<Task> getEnqueuedTasks(long limit);

    List<Task> getTasksByCorrelationID(String correlationID);

    /**
     * Return tasks that are marked disposable and do not have dependants. These tasks are suitable for immediate
     * deletion.
     *
     * @return list of marked tasks without dependants
     */
    List<Task> getMarkedTasksWithoutDependants();

    /**
     * Returns all task identifiers in clustered container.
     *
     * @return the service names
     */
    Collection<String> getTaskIds();
}
