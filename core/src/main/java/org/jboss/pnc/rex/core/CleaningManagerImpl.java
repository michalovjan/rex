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

import lombok.extern.slf4j.Slf4j;
import org.jboss.pnc.rex.core.api.CleaningManager;
import org.jboss.pnc.rex.core.api.TaskContainer;
import org.jboss.pnc.rex.core.api.TaskController;
import org.jboss.pnc.rex.model.Task;

import javax.enterprise.context.ApplicationScoped;
import javax.transaction.Transactional;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@ApplicationScoped
public class CleaningManagerImpl implements CleaningManager {

    private final TaskContainer container;
    private final TaskController controller;

    public CleaningManagerImpl(TaskContainer container, TaskController controller) {
        this.container = container;
        this.controller = controller;
    }

    @Override
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void tryClean() {
        log.info("CLEANER: Querying for tasks ready for deletion.");

        List<Task> tasksToDelete = container.getMarkedTasksWithoutDependants();

        if (tasksToDelete.isEmpty()) {
            log.info("CLEANER: No immediately disposable tasks were found.");
            return;
        }

        log.info("CLEANER: Found {} top-level tasks for deletion {}. The deletion can cascade to their dependencies.",
                tasksToDelete.size(),
                tasksToDelete.stream().map(Task::getName).collect(Collectors.toList())
        );

        tasksToDelete.forEach(task -> controller.delete(task.getName()));

        log.info("CLEANER: Cleaning completed.");
    }
}
