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
package org.jboss.pnc.rex.facade.mapper;

import org.jboss.pnc.rex.model.Task;
import org.jboss.pnc.rex.dto.TaskDTO;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = MapperCentralConfig.class,
        uses = {RequestMapper.class, ServerResponseMapper.class, ConfigurationMapper.class, TransitionTimeMapper.class})
public interface TaskMapper extends EntityMapper<TaskDTO, Task> {

    @Override
    @BeanMapping(ignoreUnmappedSourceProperties = {"unfinishedDependencies", "serverResponses", "starting", "controllerMode", "disposable"})
    TaskDTO toDTO(Task dbEntity);

    @Override
    @Mapping(target = "controllerMode", ignore = true)
    @Mapping(target = "unfinishedDependencies", ignore = true)
    @Mapping(target = "serverResponse", ignore = true)
    @Mapping(target = "dependant", ignore = true)
    @Mapping(target = "dependency", ignore = true)
    @Mapping(target = "starting", ignore = true)
    @Mapping(target = "disposable", ignore = true)
    @BeanMapping(ignoreUnmappedSourceProperties = {"stopFlag"})
    Task toDB(TaskDTO dtoEntity);
}
