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

import org.jboss.pnc.rex.common.enums.Mode;

/**
 * This is API for TaskController.
 * <p>
 * TaskController is the entity that handles transitions and schedules internal Jobs for each Task. It is a public API
 * for interacting with Tasks.
 *
 * <p>
 * TaskController does not hold any data.
 *
 * @author Jan Michalov <jmichalo@redhat.com>
 */
public interface TaskController {
    /**
     * Sets mode of a Task. Needs to be called in a transaction.
     *
     * @param name id of the Task
     * @param mode the mode
     */
    void setMode(String name, Mode mode);

    /**
     * Sets mode of a Task. Needs to be called in a transaction. Additionally, pokes queue
     * after transaction succeeds if specified.
     *
     * @param name id of the Task
     * @param mode the mode
     * @param pokeQueue should pokeQueue
     */
    void setMode(String name, Mode mode, boolean pokeQueue);

    /**
     * Method used for positive callback. Needs to be called in a transaction.
     *
     * f.e. to signalize that remote Task has started/cancelled/finished.
     * @param name id of the Task
     */
    void accept(String name, Object response);

    /**
     * Method used for negative callback. Needs to be called in a transaction.
     *
     * f.e. to signalize that remote Task failed to start/cancel or failed during execution.
     * @param name id of the Task
     */
    void fail(String name, Object response);

    void dequeue(String name);

    /**
     * Deletes a Task. The method deletes also cascades on dependencies.
     *
     * A Task can be removed only if all of its dependants are removed and is marked for disposal.
     *
     * @param name id of the Task
     */
    void delete(String name);

    /**
     * Marks the Task for disposal/cleaning.
     *
     * @param name        id of thr Task
     * @param pokeCleaner
     */
    void markForDisposal(String name, boolean pokeCleaner);
}