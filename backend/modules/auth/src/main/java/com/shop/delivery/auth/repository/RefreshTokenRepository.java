package com.shop.delivery.auth.repository;

import com.shop.delivery.auth.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    @Modifying
    @Query("UPDATE RefreshToken t SET t.revoked = true WHERE t.id = :id")
    int revokeById(@Param("id") UUID id);

    @Modifying
    @Query("UPDATE RefreshToken t SET t.revoked = true WHERE t.adminUserId = :adminUserId")
    int revokeAllByAdminUserId(@Param("adminUserId") Long adminUserId);

    @Modifying
    @Query("DELETE FROM RefreshToken t WHERE t.expiresAt < :before")
    int deleteExpiredBefore(@Param("before") Instant before);
}
