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
package org.jboss.pnc.rex.core.mapper;

import org.jboss.pnc.rex.core.model.InitialTask;
import org.jboss.pnc.rex.facade.mapper.MapperCentralConfig;
import org.jboss.pnc.rex.model.Task;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.TreeSet;

@Mapper(config = MapperCentralConfig.class, imports = {TreeSet.class})
public interface InitialTaskMapper {

    @Mapping(target = "serverResponses", ignore = true)
    @Mapping(target = "dependants", ignore = true)
    @Mapping(target = "dependencies", ignore = true)
    // initial values
    @Mapping(target = "controllerMode", source = "controllerMode", defaultValue = "ACTIVE")
    @Mapping(target = "unfinishedDependencies", constant = "0")
    @Mapping(target = "stopFlag", constant = "NONE")
    @Mapping(target = "state", constant = "NEW")
    @Mapping(target = "starting", constant = "false")
    @Mapping(target = "disposable", constant = "false")
    @Mapping(target = "timestamps", expression = "java( new TreeSet() )")
    // Singular additions
    @Mapping(target = "serverResponse", ignore = true)
    @Mapping(target = "dependant", ignore = true)
    @Mapping(target = "dependency", ignore = true)
    Task fromInitialTask(InitialTask initialTask);
}
