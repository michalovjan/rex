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
package org.jboss.pnc.rex.core.delegates;

import io.quarkus.arc.Unremovable;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.jboss.pnc.rex.core.api.QueueManager;

import javax.enterprise.context.ApplicationScoped;

@WithRetries
@Unremovable
@ApplicationScoped
public class TolerantQueueManager implements QueueManager {

    private final QueueManager delegate;

    public TolerantQueueManager(QueueManager manager) {
        this.delegate = manager;
    }

    @Override
    @Retry
    public void poke() {
        delegate.poke();
    }

    @Override
    @Retry
    public void decreaseRunningCounter() {
        delegate.decreaseRunningCounter();
    }

    @Override
    @Retry
    public void setMaximumConcurrency(Long amount) {
        delegate.setMaximumConcurrency(amount);
    }

    @Override
    public Long getMaximumConcurrency() {
        return delegate.getMaximumConcurrency();
    }
}
