package com.boeing.user.repository;

import com.boeing.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmailAndEnabledTrue(String email);
    Optional<User> findByEmail(String email);
    Optional<User> findByPhoneAndEnabledTrue(String phone);
    List<User> findAllByEnabledTrue();
    Optional<User> findByIdAndEnabledTrue(UUID id);

    Optional<User> findByPhone(String phone);
}