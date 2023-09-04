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

/**
 * Independent manager for cleaning/disposing of Tasks in Rex.
 */
public interface CleaningManager {

    /**
     * Queries and deletes all Tasks that can be removed. The tasks have to be marked as disposable
     * {@link org.jboss.pnc.rex.model.Task#disposable} and have no dependants. One-by-one the deletion is triggered on
     * these tasks.
     *
     * Each deletion cascades on dependencies but the same conditions apply (marked + no dependants). For
     * the cascaded dependencies, the dependant from which the deletion was triggered is at the time of triggering
     * already removed (to retain conditions for removal).
     *
     * The method can be called at any time, even when there is nothing to clean.
     */
    void tryClean();
}
