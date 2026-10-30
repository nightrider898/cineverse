package com.cineverse.repository;

import com.cineverse.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    @Query("select u from User u where lower(u.email) = lower(:email)")
    Optional<User> findByEmail(@Param("email") String email);

    @Query("select count(u) > 0 from User u where lower(u.email) = lower(:email)")
    boolean existsByEmail(@Param("email") String email);

    @Query("""
        select distinct u from User u left join u.roles r
        where (:q is null or lower(u.email) like lower(concat('%', cast(:q as string), '%')) or lower(u.fullName) like lower(concat('%', cast(:q as string), '%')))
          and (:role is null or r.name = :role)
        """)
    Page<User> search(@Param("q") String q, @Param("role") String role, Pageable pageable);

    long countByCreatedAtAfter(Instant after);
}
