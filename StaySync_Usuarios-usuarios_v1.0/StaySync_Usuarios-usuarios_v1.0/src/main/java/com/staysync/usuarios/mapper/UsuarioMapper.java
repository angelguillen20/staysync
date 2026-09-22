package com.staysync.usuarios.mapper;

import com.staysync.usuarios.dto.request.RegistroRequest;
import com.staysync.usuarios.dto.response.UsuarioResponse;
import com.staysync.usuarios.model.Usuario;
import org.mapstruct.*;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface UsuarioMapper {

    @Mapping(target = "id",           ignore = true)
    @Mapping(target = "passwordHash", ignore = true)
    @Mapping(target = "activo",       constant = "true")
    @Mapping(target = "createdAt",    ignore = true)
    @Mapping(target = "updatedAt",    ignore = true)
    Usuario toEntity(RegistroRequest request);

    UsuarioResponse toResponse(Usuario usuario);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "passwordHash", ignore = true)
    @Mapping(target = "email",        ignore = true)
    @Mapping(target = "rol",          ignore = true)
    void updateEntityFromRequest(@MappingTarget Usuario usuario,
                                 com.staysync.usuarios.dto.request.ActualizarUsuarioRequest request);
}
