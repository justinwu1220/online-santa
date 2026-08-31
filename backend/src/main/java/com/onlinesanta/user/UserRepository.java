package com.onlinesanta.user;

import java.time.Instant;
import java.util.Optional;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByFirebaseUid(String firebaseUid);

    Optional<User> findByEmailIgnoreCase(String email);

    /** 依角色分組計數，供監控中心的統計使用。 */
    @Query("select u.role, count(u) from User u group by u.role")
    List<Object[]> countByRole();

    List<User> findByIdIn(Collection<UUID> ids);

    /**
     * 該年度以指定角色註冊的新使用者數，供年度營運頁的「新捐贈者」使用。
     *
     * <p>JIT provisioning（見 {@code UserProvisioningService}）一律先建立 DONOR 帳號，
     * 因此以 {@code role = DONOR} 篩選即涵蓋當年新註冊的一般民眾；日後升級為機構成員
     * 或管理員的帳號不會被重複計入——那兩者分別由「新加入機構」與白名單管理員自行追蹤。
     */
    @Query("select count(u) from User u where u.role = :role and u.createdAt >= :from and u.createdAt < :to")
    long countByRoleAndCreatedAtBetween(@Param("role") UserRole role,
                                        @Param("from") Instant from, @Param("to") Instant to);
}
