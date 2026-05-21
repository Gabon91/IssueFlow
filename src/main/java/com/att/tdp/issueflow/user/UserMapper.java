package com.att.tdp.issueflow.user;

import com.att.tdp.issueflow.user.dto.UserResponse;
import org.mapstruct.Mapper;

/** MapStruct mapper between {@link User} entities and DTOs. */
@Mapper(componentModel = "spring")
public interface UserMapper {

    UserResponse toResponse(User user);
}
