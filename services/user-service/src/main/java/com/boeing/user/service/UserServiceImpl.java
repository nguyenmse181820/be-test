package com.boeing.user.service;

import com.boeing.user.dto.request.UpdateProfileRequest;
import com.boeing.user.dto.response.UserDTO;
import com.boeing.user.entity.User;
import com.boeing.user.exception.BusinessLogicException;
import com.boeing.user.exception.DuplicateResourceException;
import com.boeing.user.exception.ResourceNotFoundException;
import com.boeing.user.mapper.UserMapper;
import com.boeing.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserServiceImpl.class);

    private final UserRepository userRepository;

    @Override
    public List<UserDTO> findAll() {
        logger.info("Fetching all enabled users");
        List<User> users = userRepository.findAllByEnabledTrue();
        return users.stream()
                .map(UserMapper.INSTANCE::toDto)
                .collect(Collectors.toList());
    }

    @Override
    public UserDTO findById(UUID id) {
        logger.info("Fetching user by id: {}", id);
        User user = userRepository.findByIdAndEnabledTrue(id)
                .orElseThrow(() -> {
                    logger.warn("User not found or not enabled with id: {}", id);
                    return new ResourceNotFoundException("User", "id", id.toString());
                });
        return UserMapper.INSTANCE.toDto(user);
    }

//    @Override
//    public User findEntityById(UUID id) {
//        return userRepository.findByIdAndEnabledTrue(id)
//                .orElseThrow(() -> new RuntimeException("User not found or not enabled with id: " + id));
//    }
//
//    @Override
//    public User findEntityByPhone(String phone) {
//        return userRepository.findByPhoneAndEnabledTrue(phone)
//                .orElseThrow(() -> new RuntimeException("User not found or not enabled with phone: " + phone));
//    }
//
//
//    @Override
//    public UserDTO findByEmail(String email) {
//        User user = userRepository.findByEmailAndEnabledTrue(email)
//                .orElseThrow(() -> new RuntimeException("User not found or not enabled with email: " + email));
//        return UserMapper.INSTANCE.toDto(user);
//    }

    @Override
    public UserDTO updateProfile(UUID id, UpdateProfileRequest request) {
        logger.info("Updating profile for user id: {}", id);
        User userToUpdate = userRepository.findByIdAndEnabledTrue(id)
                .orElseThrow(() -> {
                    logger.warn("User not found or not enabled for update with id: {}", id);
                    return new ResourceNotFoundException("User", "id", id.toString());
                });

        if (StringUtils.hasText(request.getEmail()) && !request.getEmail().equalsIgnoreCase(userToUpdate.getEmail())) {
            logger.debug("Attempting to update email for user {} to {}", id, request.getEmail());
            userRepository.findByEmail(request.getEmail()).ifPresent(existingUser -> {
                if (!existingUser.getId().equals(id)) {
                    logger.warn("Update failed: Email {} already in use by another account (user id: {}).", request.getEmail(), existingUser.getId());
                    throw new DuplicateResourceException("User", "email", request.getEmail());
                }
            });
            userToUpdate.setEmail(request.getEmail());
        }

        if (StringUtils.hasText(request.getPhone()) && !request.getPhone().equals(userToUpdate.getPhone())) {
            logger.debug("Attempting to update phone for user {} to {}", id, request.getPhone());
            userRepository.findByPhoneAndEnabledTrue(request.getPhone()).ifPresent(existingUser -> {
                if (!existingUser.getId().equals(id)) {
                    logger.warn("Update failed: Phone {} already in use by another active account (user id: {}).", request.getPhone(), existingUser.getId());
                    throw new DuplicateResourceException("User", "phone", request.getPhone());
                }
            });
            userToUpdate.setPhone(request.getPhone());
        }

        if (StringUtils.hasText(request.getFirstName())) {
            userToUpdate.setFirstName(request.getFirstName());
        }
        if (StringUtils.hasText(request.getLastName())) {
            userToUpdate.setLastName(request.getLastName());
        }
        if (request.getDob() != null) {
            userToUpdate.setDob(request.getDob());
        }
        if (StringUtils.hasText(request.getGender())) {
            userToUpdate.setGender(request.getGender().toUpperCase());
        }
        if (StringUtils.hasText(request.getNationality())) {
            userToUpdate.setNationality(request.getNationality());
        }

        User updatedUser = userRepository.save(userToUpdate);
        logger.info("Profile updated successfully for user: {}", updatedUser.getEmail());
        return UserMapper.INSTANCE.toDto(updatedUser);
    }

    @Override
    public Object disable(UUID id) {
        logger.info("Attempting to disable user with id: {}", id);
        User userToDisable = userRepository.findById(id)
                .orElseThrow(() -> {
                    logger.warn("Disable user failed: User not found with id: {}", id);
                    return new ResourceNotFoundException("User", "id", id.toString());
                });

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserDetails) {
            String currentUsername = ((UserDetails) authentication.getPrincipal()).getUsername();
            if (userToDisable.getEmail().equals(currentUsername)) {
                logger.warn("Disable user failed: User {} attempted to disable their own account.", currentUsername);
                throw new BusinessLogicException("You cannot disable your own account.");
            }
        } else {
            logger.error("Disable user failed: Could not determine current authenticated user for id {}.", id);
            throw new BusinessLogicException("Unable to determine current user. Operation denied.");
        }

        if (!userToDisable.isEnabled()) {
            logger.info("User {} is already disabled.", userToDisable.getEmail());
            return Map.of("message", "User " + id + " is already disabled.");
        }

        userToDisable.setEnabled(false);
        userRepository.save(userToDisable);
        logger.info("User {} (id: {}) has been disabled successfully.", userToDisable.getEmail(), id);
        return Map.of("message", "User " + id + " has been disabled.");
    }

    @Override
    public boolean canUserAccessProfile(UUID targetUserId, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || !(authentication.getPrincipal() instanceof UserDetails currentUserDetails)) {
            logger.debug("canUserAccessProfile check: Authentication is null, not authenticated, or principal is not UserDetails.");
            return false;
        }

        String currentUsername = currentUserDetails.getUsername();
        logger.debug("canUserAccessProfile check for targetUserId: {}, currentUser: {}", targetUserId, currentUsername);

        if (currentUserDetails.getAuthorities().stream().anyMatch(auth -> auth.getAuthority().equals("ADMIN"))) {
            logger.debug("canUserAccessProfile check: Current user {} is ADMIN. Access granted for targetUserId: {}", currentUsername, targetUserId);
            return true;
        }

        if (currentUserDetails instanceof User currentUserEntity && currentUserEntity.getId().equals(targetUserId)) {
            logger.debug("canUserAccessProfile check: Current user {} is accessing their own profile (ID match). Access granted for targetUserId: {}", currentUsername, targetUserId);
            return true;
        }

        Optional<User> targetUserOptional = userRepository.findById(targetUserId);
        if (targetUserOptional.isPresent()) {
            User targetUser = targetUserOptional.get();
            boolean accessGranted = targetUser.getEmail().equals(currentUsername);
            logger.debug("canUserAccessProfile check: Target user email: {}, Current user email: {}. Access: {}", targetUser.getEmail(), currentUsername, accessGranted);
            return accessGranted;
        }

        logger.debug("canUserAccessProfile check: Target user with id {} not found. Access denied.", targetUserId);
        return false;
    }
}