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

import javax.enterprise.context.ApplicationScoped;
import javax.transaction.Transactional;

import io.quarkus.arc.Unremovable;
import org.jboss.pnc.rex.common.enums.Mode;
import org.jboss.pnc.rex.core.api.TaskController;

@WithTransactions
@Unremovable
@ApplicationScoped
public class TransactionalTaskController implements TaskController {

    private final TaskController delegate;

    public TransactionalTaskController(TaskController controller) {
        this.delegate = controller;
    }

    @Override
    @Transactional
    public void setMode(String name, Mode mode) {
        delegate.setMode(name, mode);
    }

    @Override
    @Transactional
    public void setMode(String name, Mode mode, boolean pokeQueue) {
        delegate.setMode(name, mode, pokeQueue);
    }

    @Override
    @Transactional
    public void accept(String name, Object response) {
        delegate.accept(name, response);
    }

    @Override
    @Transactional
    public void fail(String name, Object response) {
        delegate.fail(name, response);
    }

    @Override
    @Transactional
    public void dequeue(String name) {
        delegate.dequeue(name);
    }

    @Override
    @Transactional
    public void delete(String name) {
        delegate.delete(name);
    }

    @Override
    @Transactional
    public void markForDisposal(String name, boolean pokeCleaner) {
        delegate.markForDisposal(name, pokeCleaner);
    }
}
