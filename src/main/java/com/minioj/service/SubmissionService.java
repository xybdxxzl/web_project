package com.minioj.service;

import com.minioj.dto.CurrentUser;
import com.minioj.dto.SubmissionAccepted;
import com.minioj.dto.SubmissionPage;
import com.minioj.dto.SubmissionRequest;
import com.minioj.dto.SubmissionResult;
import com.minioj.entity.Submission;
import com.minioj.entity.Role;
import com.minioj.exception.BizException;
import com.minioj.judge.JudgeEngine;
import com.minioj.judge.JudgeJob;
import com.minioj.repository.ProblemRepository;
import com.minioj.repository.SubmissionRepository;
import com.minioj.repository.TestCaseRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class SubmissionService {

    private final SubmissionRepository submissions;
    private final ProblemRepository problems;
    private final TestCaseRepository testCases;
    private final JudgeEngine judge;
    private final TransactionTemplate transaction;

    public SubmissionService(SubmissionRepository submissions, ProblemRepository problems, TestCaseRepository testCases,
                             JudgeEngine judge, PlatformTransactionManager transactionManager) {
        this.submissions = submissions;
        this.problems = problems;
        this.testCases = testCases;
        this.judge = judge;
        this.transaction = new TransactionTemplate(transactionManager);
        this.transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }

    public SubmissionAccepted submit(Long userId, SubmissionRequest request) {
        JudgeJob job = transaction.execute(status -> {
            var problem = problems.findById(request.problemId())
                    .orElseThrow(() -> new BizException(HttpStatus.NOT_FOUND, "题目不存在"));
            var cases = testCases.findByProblemIdOrderByIdAsc(problem.getId());
            if (cases.isEmpty()) {
                throw new BizException(HttpStatus.CONFLICT, "题目尚未配置测试用例");
            }
            var submission = submissions.save(new Submission(userId, problem.getId(), request.language(), request.code()));
            return new JudgeJob(submission.getId(), request.language(), request.code(), problem.getTimeLimitMs(),
                    problem.getMemoryLimitMb(), cases);
        });
        judge.enqueue(job);
        return new SubmissionAccepted(job.submissionId());
    }

    public SubmissionPage history(Long userId, int page, int size) {
        var result = submissions.findHistory(userId, PageRequest.of(page - 1, size));
        return new SubmissionPage(result.getContent(), page, size, result.getTotalElements(), result.getTotalPages());
    }

    public SubmissionResult result(CurrentUser viewer, Long id) {
        var submission = submissions.findById(id)
                .orElseThrow(() -> new BizException(HttpStatus.NOT_FOUND, "提交不存在"));
        if (viewer.role() != Role.ADMIN && !submission.getUserId().equals(viewer.id())) {
            throw new BizException(HttpStatus.FORBIDDEN, "无权查看此提交");
        }
        return new SubmissionResult(submission.getId(), submission.getProblemId(), submission.getLanguage(),
                submission.getStatus(), submission.getTimeUsedMs(), submission.getMemoryUsedKb(),
                submission.getErrorMessage(), submission.getCode(), submission.getCreatedAt());
    }
}
