from enum import Enum

from models.schemas import (
    PipelineProgress,
    PipelineRequest,
    ProblemSchema,
    TestCaseSchema,
)
from services.problem_analyzer import analyze_problem
from services.problem_cache import load_cache, save_cache
from services.testcase_generator import generate_testcases


class PipelineState(Enum):
    IDLE = "IDLE"
    CLASSIFYING = "CLASSIFYING"
    GENERATING_SMALL = "GENERATING_SMALL"
    VALIDATING_ORACLE = "VALIDATING_ORACLE"
    STRESS_TESTING = "STRESS_TESTING"
    GENERATING_MEDIUM = "GENERATING_MEDIUM"
    GENERATING_KILLER = "GENERATING_KILLER"
    QUALITY_GATE = "QUALITY_GATE"
    DONE = "DONE"
    ERROR = "ERROR"


class PipelineOrchestrator:
    def __init__(self, request: PipelineRequest):
        self.request = request
        self.target = max(1, min(request.count or 10, 50))
        self.warnings: list[str] = []
        self.problem: ProblemSchema | None = None
        self.small_cases: list[TestCaseSchema] = []
        self.medium_cases: list[TestCaseSchema] = []
        self.killer_cases: list[TestCaseSchema] = []

    async def run(self):
        try:
            cached = load_cache(self.request.problem_text)
            if cached:
                yield self._progress_from_cache(cached)
                return

            yield self._progress(
                PipelineState.CLASSIFYING,
                "Dang phan tich va phan loai dang bai...",
                5,
            )
            self.problem = analyze_problem(self.request.problem_text)
            yield self._progress(
                PipelineState.CLASSIFYING,
                f"Da nhan dien dang bai: {self.problem.problem_type or 'UNKNOWN'}",
                10,
            )

            plan = _build_test_pyramid_plan(self.target)
            yield self._progress(
                PipelineState.GENERATING_SMALL,
                "Dang sinh SMALL test cases theo problem type...",
                18,
            )
            self.small_cases = _cases_from_response(
                generate_testcases(
                    self.problem,
                    plan.small_count,
                    self.request.include_edge_cases,
                    "SMALL",
                )
            )
            yield self._progress(
                PipelineState.VALIDATING_ORACLE,
                "Dang chuan bi oracle validation o Java layer...",
                32,
            )
            yield self._progress(
                PipelineState.STRESS_TESTING,
                "Dang chuan bi stress agent o Java layer...",
                45,
            )

            yield self._progress(
                PipelineState.GENERATING_MEDIUM,
                "Dang sinh MEDIUM test cases...",
                60,
            )
            self.medium_cases = _cases_from_response(
                generate_testcases(
                    self.problem,
                    plan.medium_count,
                    self.request.include_edge_cases,
                    "MEDIUM",
                    _inputs_of(self.small_cases),
                )
            )

            yield self._progress(
                PipelineState.GENERATING_KILLER,
                "Dang sinh KILLER test cases...",
                75,
            )
            self.killer_cases = _cases_from_response(
                generate_testcases(
                    self.problem,
                    plan.killer_count,
                    self.request.include_edge_cases,
                    "KILLER",
                    _inputs_of(self.small_cases + self.medium_cases),
                )
            )

            yield self._progress(
                PipelineState.QUALITY_GATE,
                "Dang kiem tra do kho cua KILLER cases...",
                88,
            )
            self._quality_gate()

            all_cases = self._all_cases()[: self.target]
            payload = {
                "problem": self.problem.model_dump() if self.problem else None,
                "all_testcases": [case.model_dump() for case in all_cases],
                "warnings": self.warnings,
            }
            save_cache(self.request.problem_text, payload)

            yield self._progress(
                PipelineState.DONE,
                "Pipeline hoan thanh.",
                100,
                all_cases,
            )
        except Exception as exc:
            self.warnings.append(str(exc))
            yield self._progress(
                PipelineState.ERROR,
                "Pipeline loi: " + str(exc),
                100,
            )

    def _quality_gate(self) -> None:
        if not self.killer_cases:
            self.warnings.append("Khong co KILLER cases de kiem tra do kho.")
            return

        small_tokens = _average_token_count(self.small_cases)
        killer_tokens = _average_token_count(self.killer_cases)
        if killer_tokens <= max(20.0, small_tokens * 1.5):
            self.warnings.append(
                "KILLER cases co ve qua nho so voi SMALL cases; da thu regenerate voi profile KILLER."
            )
            regenerated = _cases_from_response(
                generate_testcases(
                    self.problem,
                    len(self.killer_cases),
                    self.request.include_edge_cases,
                    "KILLER",
                    _inputs_of(self._all_cases()),
                )
            )
            if regenerated:
                self.killer_cases = regenerated

    def _progress(
        self,
        state: PipelineState,
        message: str,
        progress_pct: int,
        cases: list[TestCaseSchema] | None = None,
    ) -> PipelineProgress:
        all_cases = cases if cases is not None else self._all_cases()
        return PipelineProgress(
            state=state.value,
            message=message,
            progress_pct=max(0, min(100, progress_pct)),
            testcases_ready=len(all_cases),
            testcases_target=self.target,
            warnings=list(self.warnings),
            problem=self.problem,
            all_testcases=all_cases if state == PipelineState.DONE else [],
            cached=False,
        )

    def _progress_from_cache(self, cached: dict) -> PipelineProgress:
        problem_data = cached.get("problem") or {}
        case_data = cached.get("all_testcases") or []
        problem = ProblemSchema(**problem_data) if problem_data else None
        cases = [TestCaseSchema(**case) for case in case_data if isinstance(case, dict)]
        warnings = list(cached.get("warnings") or [])
        return PipelineProgress(
            state=PipelineState.DONE.value,
            message="Dung ket qua pipeline da cache.",
            progress_pct=100,
            testcases_ready=len(cases),
            testcases_target=self.target,
            warnings=warnings,
            problem=problem,
            all_testcases=cases[: self.target],
            cached=True,
        )

    def _all_cases(self) -> list[TestCaseSchema]:
        return self.small_cases + self.medium_cases + self.killer_cases


def _cases_from_response(response) -> list[TestCaseSchema]:
    return list(response.testcases or [])


def _inputs_of(cases: list[TestCaseSchema]) -> list[str]:
    return [case.input for case in cases if case and case.input]


def _average_token_count(cases: list[TestCaseSchema]) -> float:
    if not cases:
        return 0.0
    counts = [len((case.input or "").split()) for case in cases]
    return sum(counts) / len(counts)


def _build_test_pyramid_plan(total: int):
    if total == 1:
        return TestPyramidPlan(1, 0, 0)
    small = max(1, round(total * 0.2))
    medium = max(1, round(total * 0.4))
    killer = total - small - medium
    if killer < 0:
        medium = max(0, medium + killer)
        killer = 0
    if total >= 3 and killer == 0:
        if medium > 1:
            medium -= 1
        else:
            small = max(1, small - 1)
        killer = 1
    return TestPyramidPlan(small, medium, killer)


class TestPyramidPlan:
    def __init__(self, small_count: int, medium_count: int, killer_count: int):
        self.small_count = small_count
        self.medium_count = medium_count
        self.killer_count = killer_count
