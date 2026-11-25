package com.minioj.repository;

import java.util.List;

import com.minioj.dto.SubmissionSummary;
import com.minioj.entity.JudgeStatus;
import com.minioj.entity.Submission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface SubmissionRepository extends JpaRepository<Submission, Long> {

    @Query(value = """
            select new com.minioj.dto.SubmissionSummary(s.id, s.problemId, p.title, s.language,
                                                       s.status, s.timeUsedMs, s.createdAt)
            from Submission s left join Problem p on s.problemId = p.id
            where s.userId = :userId order by s.createdAt desc, s.id desc
            """, countQuery = "select count(s) from Submission s where s.userId = :userId")
    Page<SubmissionSummary> findHistory(@Param("userId") Long userId, Pageable pageable);

    @Modifying
    @Transactional
    @Query("update Submission s set s.status = :next where s.id = :id and s.status = :previous")
    int transition(@Param("id") Long id, @Param("previous") JudgeStatus previous, @Param("next") JudgeStatus next);

    @Modifying
    @Transactional
    @Query("""
            update Submission s set s.status = :status, s.timeUsedMs = :elapsed, s.errorMessage = :message
            where s.id = :id and s.status in :activeStatuses
            """)
    void finish(@Param("id") Long id, @Param("activeStatuses") List<JudgeStatus> activeStatuses,
                @Param("status") JudgeStatus status, @Param("elapsed") Long elapsed, @Param("message") String message);

    @Modifying
    @Transactional
    @Query("update Submission s set s.status = :status, s.errorMessage = :message where s.status in :activeStatuses")
    void finishInterrupted(@Param("activeStatuses") List<JudgeStatus> activeStatuses,
                           @Param("status") JudgeStatus status, @Param("message") String message);
}
