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

    /** 管理員的認領者清單：只列一般認領者，依信箱、顯示名稱或真實姓名搜尋（q 永遠有值，沒搜尋傳空字串）。 */
    @Query("""
            select u from User u
            where u.role = com.onlinesanta.user.UserRole.DONOR
              and (:q = '' or lower(u.email) like :q or lower(u.displayName) like :q or lower(u.name) like :q)
            order by u.createdAt desc
            """)
    org.springframework.data.domain.Page<User> searchDonors(@Param("q") String q,
                                                            org.springframework.data.domain.Pageable pageable);

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
