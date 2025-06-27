package com.boeing.user.mapper;

import com.boeing.user.dto.response.UserDTO;
import com.boeing.user.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper
public interface UserMapper {
    UserMapper INSTANCE = Mappers.getMapper(UserMapper.class);

    @Mapping(target = "id", source = "id")
    @Mapping(target = "email", source = "email")
    @Mapping(target = "firstName", source = "firstName")
    @Mapping(target = "lastName", source = "lastName")
    @Mapping(target = "dob", source = "dob")
    @Mapping(target = "phone", source = "phone")
    @Mapping(target = "gender", source = "gender")
    @Mapping(target = "enabled", source = "enabled")
    @Mapping(target = "nationality", source = "nationality")
    @Mapping(target = "role", source = "role")
    UserDTO toDto(User user);

    @Mapping(target = "id", source = "id")
    @Mapping(target = "email", source = "email")
    @Mapping(target = "firstName", source = "firstName")
    @Mapping(target = "lastName", source = "lastName")
    @Mapping(target = "dob", source = "dob")
    @Mapping(target = "phone", source = "phone")
    @Mapping(target = "gender", source = "gender")
    @Mapping(target = "enabled", source = "enabled")
    @Mapping(target = "nationality", source = "nationality")
    @Mapping(target = "role", source = "role")
    User toEntity(UserDTO dto);
}