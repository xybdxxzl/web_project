package com.minioj.repository;

import java.util.List;

import com.minioj.entity.TestCase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TestCaseRepository extends JpaRepository<TestCase, Long> {

    List<TestCase> findByProblemIdOrderByIdAsc(Long problemId);

    List<TestCase> findByProblemIdAndSampleTrueOrderByIdAsc(Long problemId);

    int countByProblemId(Long problemId);

    @Modifying
    @Query("delete from TestCase t where t.problemId = :problemId")
    void deleteForProblem(@Param("problemId") Long problemId);
}
