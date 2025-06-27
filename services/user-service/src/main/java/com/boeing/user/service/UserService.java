package com.boeing.user.service;

import com.boeing.user.dto.request.UpdateProfileRequest;
import com.boeing.user.dto.response.UserDTO;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.UUID;

public interface UserService {
    List<UserDTO> findAll();
    UserDTO findById(UUID id);
//    User findEntityById(UUID id);
//    User findEntityByPhone(String phone);
//    UserDTO findByEmail(String email);
    UserDTO updateProfile(UUID id, UpdateProfileRequest request);
    Object disable(UUID id);
    boolean canUserAccessProfile(UUID targetUserId, Authentication authentication);
}
