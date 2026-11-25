package com.minioj.service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.minioj.dto.ProblemDetails;
import com.minioj.dto.ProblemDetails.TestCaseView;
import com.minioj.dto.ProblemPage;
import com.minioj.dto.ProblemRequest;
import com.minioj.dto.ProblemSummary;
import com.minioj.dto.ProblemSummary.UserStatus;
import com.minioj.entity.Difficulty;
import com.minioj.entity.Problem;
import com.minioj.entity.TestCase;
import com.minioj.exception.BizException;
import com.minioj.repository.ProblemRepository;
import com.minioj.repository.SubmissionRepository;
import com.minioj.repository.SubmissionRepository.ProblemCounts;
import com.minioj.repository.TestCaseRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProblemService {

    private final ProblemRepository problems;
    private final TestCaseRepository testCases;
    private final SubmissionRepository submissions;

    public ProblemService(ProblemRepository problems, TestCaseRepository testCases, SubmissionRepository submissions) {
        this.problems = problems;
        this.testCases = testCases;
        this.submissions = submissions;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ProblemPage list(int page, int size, Difficulty difficulty, String keyword, Long userId) {
        Page<Problem> result = problems.search(difficulty, keyword.strip(), PageRequest.of(page - 1, size, Sort.by("id")));
        List<Long> ids = result.getContent().stream().map(Problem::getId).toList();
        Map<Long, ProblemCounts> counts = ids.isEmpty() ? Map.of() : submissions.countForProblems(ids, userId).stream()
                .collect(Collectors.toMap(ProblemCounts::problemId, Function.identity()));
        List<ProblemSummary> items = result.getContent().stream()
                .map(problem -> summary(problem, counts.get(problem.getId())))
                .toList();
        return new ProblemPage(items, page, size, result.getTotalElements(), result.getTotalPages());
    }

    private ProblemSummary summary(Problem problem, ProblemCounts counts) {
        UserStatus status = counts.userAcCount() > 0 ? UserStatus.AC
                : counts.userSubmitCount() > 0 ? UserStatus.TRIED : UserStatus.NONE;
        return new ProblemSummary(problem.getId(), problem.getTitle(), problem.getDifficulty(), problem.getTimeLimitMs(),
                problem.getMemoryLimitMb(), counts.submitCount(), counts.acCount(), status);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ProblemDetails detail(Long id, boolean includeHidden) {
        Problem problem = problems.findById(id)
                .orElseThrow(() -> new BizException(HttpStatus.NOT_FOUND, "题目不存在"));
        List<TestCase> cases = includeHidden
                ? testCases.findByProblemIdOrderByIdAsc(id)
                : testCases.findByProblemIdAndSampleTrueOrderByIdAsc(id);
        return detail(problem, cases);
    }

    @Transactional
    public ProblemDetails create(ProblemRequest request) {
        Problem problem = problems.save(new Problem(request.title(), request.description(),
                request.timeLimitMs(), request.memoryLimitMb(), request.difficulty()));
        return detail(problem, saveCases(problem.getId(), request));
    }

    @Transactional
    public ProblemDetails update(Long id, ProblemRequest request) {
        Problem problem = findForUpdate(id);
        problem.update(request.title(), request.description(), request.timeLimitMs(),
                request.memoryLimitMb(), request.difficulty());
        testCases.deleteForProblem(id);
        return detail(problem, saveCases(id, request));
    }

    @Transactional
    public void delete(Long id) {
        problems.delete(findForUpdate(id));
    }

    private Problem findForUpdate(Long id) {
        return problems.findForUpdate(id)
                .orElseThrow(() -> new BizException(HttpStatus.NOT_FOUND, "题目不存在"));
    }

    private List<TestCase> saveCases(Long id, ProblemRequest request) {
        List<TestCase> cases = request.testCases().stream()
                .map(testCase -> new TestCase(id, testCase.input(), testCase.expectedOutput(), testCase.isSample()))
                .toList();
        return testCases.saveAll(cases);
    }

    private ProblemDetails detail(Problem problem, List<TestCase> cases) {
        List<TestCaseView> views = cases.stream()
                .map(testCase -> new TestCaseView(testCase.getInput(), testCase.getExpectedOutput(), testCase.isSample()))
                .toList();
        return new ProblemDetails(problem.getId(), problem.getTitle(), problem.getDescription(), problem.getDifficulty(),
                problem.getTimeLimitMs(), problem.getMemoryLimitMb(), problem.getCreatedAt(), views);
    }
}
