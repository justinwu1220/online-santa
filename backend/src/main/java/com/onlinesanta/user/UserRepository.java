package com.onlinesanta.user;

import java.util.Optional;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByFirebaseUid(String firebaseUid);

    Optional<User> findByEmailIgnoreCase(String email);

    /** 依角色分組計數，供監控中心的統計使用。 */
    @Query("select u.role, count(u) from User u group by u.role")
    List<Object[]> countByRole();

    List<User> findByIdIn(Collection<UUID> ids);
}
