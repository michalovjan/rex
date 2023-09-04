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
package org.jboss.pnc.rex.core.jobs;

import lombok.Getter;
import org.jboss.pnc.rex.model.Task;

import javax.enterprise.event.TransactionPhase;
import java.util.Optional;

/**
 * Template for creating Controller Jobs. Usually a ControllerJob is associated with a specific Task which triggered
 * said Job.
 *
 * Each Controller Job has to have a TransactionPhase defined. TransactionPhase will determine in which
 * transaction phase the Job is run (f.e. a Job should be executed only after a successful Transaction or a Job has to
 * be executed within the bounds of the Transaction which scheduled the Job). Jobs that run after Transactions have to
 * handle fault tolerance (Retries) and creating new transactions on their own.
 *
 * @author Jan Michalov <jmichalo@redhat.com>
 */
public abstract class ControllerJob implements Runnable {

    @Getter
    protected TransactionPhase invocationPhase;

    protected Task context;

    @Getter
    protected boolean async;

    private boolean completed = false;

    private boolean failed = false;

    protected ControllerJob(TransactionPhase invocationPhase, Task context, boolean async) {
        this.invocationPhase = invocationPhase;
        this.context = context;
        this.async = async;
    }

    @Override
    public void run() {
        try {
            beforeExecute();
            if (!execute()) {
                failed = true;
                onFailure();
            }
        } catch (RuntimeException e) {
            failed = true;
            onException(e);
            throw e;
        } finally {
            completed = true;
            afterExecute();
        }
    }

    abstract void beforeExecute();
    abstract void afterExecute();
    abstract boolean execute();

    abstract void onFailure();
    abstract void onException(Throwable e);

    public Optional<Task> getContext() {
        return context == null ? Optional.empty() : Optional.of(context);
    }

    public boolean isFinished() {
        return completed;
    }

    public boolean isSuccessful() {
        assertTaskIsCompleted();

        return !failed;
    }

    public boolean hasFailed() {
        assertTaskIsCompleted();

        return failed;
    }

    private void assertTaskIsCompleted() {
        if (!isFinished()) {
            throw new IllegalStateException(this + " is not yet completed");
        }
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "(of = " + (context == null ? "NONE" : context.getName()) + ")";
    }
}
