package com.minioj.repository;

import java.time.Instant;
import java.util.List;

import com.minioj.dto.SiteStats.RecentSubmission;
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

    @Query("""
            select new com.minioj.repository.SubmissionRepository$StatusCount(s.status, count(s))
            from Submission s group by s.status
            """)
    List<StatusCount> countStatuses();

    @Query("""
            select new com.minioj.dto.SiteStats$RecentSubmission(s.id, u.username, s.problemId, p.title,
                                                                s.language, s.status, s.createdAt)
            from Submission s join User u on u.id = s.userId left join Problem p on p.id = s.problemId
            order by s.createdAt desc, s.id desc
            """)
    List<RecentSubmission> findRecent(Pageable pageable);

    long countByUserId(Long userId);

    @Query("""
            select count(distinct s.problemId) from Submission s
            where s.userId = :userId and s.status = AC
            """)
    long countAcceptedProblems(@Param("userId") Long userId);

    @Query("""
            select new com.minioj.repository.SubmissionRepository$Activity(s.createdAt, s.status)
            from Submission s where s.userId = :userId and s.createdAt >= :start and s.createdAt < :end
            """)
    List<Activity> findActivity(@Param("userId") Long userId, @Param("start") Instant start, @Param("end") Instant end);

    @Query(value = """
            select new com.minioj.repository.SubmissionRepository$RankingScore(
                u.id, u.username, count(distinct case when s.status = AC then s.problemId end),
                count(s.id), count(case when s.status = AC then 1 end))
            from User u left join Submission s on s.userId = u.id
            group by u.id, u.username
            order by count(distinct case when s.status = AC then s.problemId end) desc,
                     coalesce(cast(count(case when s.status = AC then 1 end) as bigdecimal(38, 18))
                              / nullif(count(s.id), 0), 0) desc,
                     u.id asc
            """, countQuery = "select count(u) from User u")
    Page<RankingScore> findRanking(Pageable pageable);

    @Query("""
            select new com.minioj.repository.SubmissionRepository$ProblemCounts(
                p.id, count(s.id), count(case when s.status = AC then 1 end),
                count(case when s.userId = :userId then 1 end),
                count(case when s.userId = :userId and s.status = AC then 1 end))
            from Problem p left join Submission s on s.problemId = p.id
            where p.id in :problemIds group by p.id
            """)
    List<ProblemCounts> countForProblems(@Param("problemIds") List<Long> problemIds, @Param("userId") Long userId);

    @Modifying
    @Transactional
    @Query("update Submission s set s.status = :next where s.id = :id and s.status = :previous")
    int transition(@Param("id") Long id, @Param("previous") JudgeStatus previous, @Param("next") JudgeStatus next);

    @Modifying
    @Transactional
    @Query("update Submission s set s.totalCases = :total where s.id = :id and s.status = JUDGING")
    void setTotalCases(@Param("id") Long id, @Param("total") int total);

    @Modifying
    @Transactional
    @Query("update Submission s set s.passedCases = s.passedCases + 1 where s.id = :id and s.status = JUDGING")
    void incrementPassedCases(@Param("id") Long id);

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

    record StatusCount(JudgeStatus status, long count) {
    }

    record Activity(Instant createdAt, JudgeStatus status) {
    }

    record RankingScore(Long userId, String username, long acProblemCount, long totalSubmissions, long acSubmissions) {
    }

    record ProblemCounts(Long problemId, long submitCount, long acCount, long userSubmitCount, long userAcCount) {
    }
}
