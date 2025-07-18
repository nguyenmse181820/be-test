package com.boeing.loyalty.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.boeing.loyalty.entity.UserVoucher;

@Repository
public interface UserVoucherRepository extends JpaRepository<UserVoucher, UUID> {
    Page<UserVoucher> findByMembership_UserId(UUID userId, Pageable pageable);
    List<UserVoucher> findByIsUsedFalseAndMembership_UserId(UUID userId);
    Optional<UserVoucher> findByCode(String code);
    Optional<UserVoucher> findByCodeAndMembership_UserId(String code, UUID userId);
}
