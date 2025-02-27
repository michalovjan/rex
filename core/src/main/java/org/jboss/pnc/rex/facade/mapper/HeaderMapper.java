package org.jboss.pnc.rex.facade.mapper;

import org.jboss.pnc.rex.dto.HeaderDTO;
import org.jboss.pnc.rex.model.Header;
import org.mapstruct.Mapper;

@Mapper(config = MapperCentralConfig.class)
public interface HeaderMapper extends EntityMapper<HeaderDTO, Header> {

    @Override
    HeaderDTO toDTO(Header dbEntity);

    @Override
    Header toDB(HeaderDTO dtoEntity);
}
