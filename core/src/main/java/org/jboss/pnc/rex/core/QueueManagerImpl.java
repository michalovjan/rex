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
import org.infinispan.client.hotrod.VersionedValue;
import org.jboss.pnc.rex.core.api.QueueManager;
import org.jboss.pnc.rex.core.api.TaskContainer;
import org.jboss.pnc.rex.core.api.TaskController;
import org.jboss.pnc.rex.core.api.TaskRegistry;
import org.jboss.pnc.rex.core.counter.Counter;
import org.jboss.pnc.rex.core.counter.MaxConcurrent;
import org.jboss.pnc.rex.core.counter.Running;
import org.jboss.pnc.rex.model.Task;

import javax.enterprise.context.ApplicationScoped;
import javax.transaction.Transactional;

import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.stream.Collectors;

import static javax.transaction.Transactional.TxType.MANDATORY;

@Slf4j
@ApplicationScoped
public class QueueManagerImpl implements QueueManager {

    private final Counter max;
    private final Counter running;
    private final TaskRegistry container;
    private final TaskController controller;

    public QueueManagerImpl(@MaxConcurrent Counter max, @Running Counter running, TaskRegistry container, TaskController controller) {
        this.max = max;
        this.running = running;
        this.container = container;
        this.controller = controller;
    }

    @Override
    @Transactional
    public void poke() {
        log.info("QUEUE: Poking Task queue");
        VersionedValue<Long> maxMetadata = max.getMetadataValue();
        Long maxValue = maxMetadata.getValue();

        VersionedValue<Long> runningMetadata = running.getMetadataValue();
        Long runningValue = runningMetadata.getValue();

        if (runningValue >= maxValue) {
            log.debug("QUEUE: Maximum number of parallel builds reached.({} out of {})", runningValue, maxValue);
            return;
        }

        long freeSpace = maxValue - runningValue;

        List<Task> randomEnqueuedTasks = container.getEnqueuedTasks(freeSpace);

        if (randomEnqueuedTasks.isEmpty()) {
            return;
        }

        log.info("QUEUE: Free space of {} found. Scheduling {} task(s) of {}",
                freeSpace,
                randomEnqueuedTasks.size(),
                randomEnqueuedTasks.stream().map(Task::getName).collect(Collectors.toList())
        );

        randomEnqueuedTasks.forEach(task -> controller.dequeue(task.getName()));

        log.info("QUEUE: Increasing running counter. ({} to {}) [ISPN-VERSION:{}]",
                runningValue,
                (runningValue + randomEnqueuedTasks.size()),
                runningMetadata.getVersion());
        if (!running.replaceValue(runningMetadata, runningValue + randomEnqueuedTasks.size())) {
            RuntimeException e = new ConcurrentModificationException("Running counter was modified concurrently.");
            log.error("QUEUE: Concurrent modification detected.", e);
            throw e;
        }
    }

    @Override
    @Transactional(MANDATORY)
    public void decreaseRunningCounter() {
        VersionedValue<Long> runningMetadata = running.getMetadataValue();
        long runningValue = runningMetadata.getValue() - 1;
        log.info("QUEUE: Decreasing running counter by one. ({} to {}) [ISPN-VERSION:{}]",
                runningMetadata.getValue(),
                runningValue,
                runningMetadata.getVersion());
        if (!running.replaceValue(runningMetadata, runningValue)) {
            RuntimeException e = new ConcurrentModificationException("Running counter was modified concurrently.");
            log.error("QUEUE: Concurrent modification detected.", e);
            throw e;
        }
    }

    @Override
    @Transactional(MANDATORY)
    public void setMaximumConcurrency(Long amount) {
        VersionedValue<Long> maxMetadata = max.getMetadataValue();
        if (maxMetadata == null) {
            max.initialize(amount);
        } else {
            max.replaceValue(maxMetadata, amount);
        }
        poke();
    }

    @Override
    public Long getMaximumConcurrency() {
        VersionedValue<Long> meta = max.getMetadataValue();
        return meta == null ? null : meta.getValue();
    }
}
