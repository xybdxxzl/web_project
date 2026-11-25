package com.minioj.repository;

import java.util.Optional;

import com.minioj.entity.Difficulty;
import com.minioj.entity.Problem;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProblemRepository extends JpaRepository<Problem, Long> {

    boolean existsByTitle(String title);

    @Query("""
            select p from Problem p
            where (:difficulty is null or p.difficulty = :difficulty)
              and (:keyword = '' or locate(lower(:keyword), lower(p.title)) > 0)
            """)
    Page<Problem> search(@Param("difficulty") Difficulty difficulty, @Param("keyword") String keyword, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Problem p where p.id = :id")
    Optional<Problem> findForUpdate(@Param("id") Long id);
}
