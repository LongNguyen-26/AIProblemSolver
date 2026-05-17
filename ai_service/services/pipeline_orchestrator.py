import asyncio
import re
from enum import Enum

from models.schemas import (
    PipelineProgress,
    PipelineRequest,
    ProblemSchema,
    TestCaseSchema,
)
from services.problem_analyzer import analyze_problem
from services.problem_cache import load_cache, save_cache
from services.testcase_generator import (
    KILLER_STRATEGIES,
    generate_single_killer_case,
    generate_testcases,
)


MAX_KILLER_RETRIES = 3


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
        self.problem_text = request.problem_text or ""
        if len(self.problem_text) > 50000:
            self.warnings.append(
                "Problem text qua dai; da cat bot xuong 50000 ky tu de tranh timeout."
            )
            self.problem_text = self.problem_text[:50000]
        self.problem: ProblemSchema | None = None
        self.small_cases: list[TestCaseSchema] = []
        self.medium_cases: list[TestCaseSchema] = []
        self.killer_cases: list[TestCaseSchema] = []

    async def run(self):
        try:
            cached = load_cache(self.problem_text)
            if cached:
                yield self._progress_from_cache(cached)
                return

            yield self._progress(
                PipelineState.CLASSIFYING,
                "Dang phan tich va phan loai dang bai...",
                5,
            )
            self.problem = analyze_problem(self.problem_text)
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
            self.small_cases = self._safe_generate_cases(
                plan.small_count,
                "SMALL",
                [],
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
            self.medium_cases = self._safe_generate_cases(
                plan.medium_count,
                "MEDIUM",
                _inputs_of(self.small_cases),
            )

            yield self._progress(
                PipelineState.GENERATING_KILLER,
                "Dang sinh KILLER test cases...",
                75,
            )
            self.killer_cases = []
            for attempt in range(MAX_KILLER_RETRIES):
                yield self._progress(
                    PipelineState.GENERATING_KILLER,
                    f"Dang sinh KILLER test cases (attempt {attempt + 1}/{MAX_KILLER_RETRIES})...",
                    75 + attempt * 4,
                )
                candidates = await self._generate_killer_parallel(plan.killer_count * 2)
                valid = [
                    case
                    for case in candidates
                    if self._is_valid_killer(case.input, case.description)
                ]
                self.killer_cases = _merge_unique_cases(self.killer_cases, valid)[
                    : plan.killer_count
                ]
                if len(self.killer_cases) >= plan.killer_count:
                    break
                self.warnings.append(
                    f"KILLER attempt {attempt + 1}: {len(self.killer_cases)}/{plan.killer_count}. Retrying."
                )

            if len(self.killer_cases) < plan.killer_count:
                yield self._progress(
                    PipelineState.GENERATING_KILLER,
                    "KILLER fallback: promoting MEDIUM cases.",
                    86,
                )
                self.killer_cases = self._promote_medium_to_killer(plan.killer_count)

            yield self._progress(
                PipelineState.QUALITY_GATE,
                "Dang kiem tra do kho cua KILLER cases...",
                88,
            )
            self._quality_gate(plan.killer_count)

            all_cases = self._all_cases()[: self.target]
            payload = {
                "problem": self.problem.model_dump() if self.problem else None,
                "all_testcases": [case.model_dump() for case in all_cases],
                "warnings": self.warnings,
            }
            save_cache(self.problem_text, payload)

            yield self._progress(
                PipelineState.DONE,
                "Pipeline hoan thanh.",
                100,
                all_cases,
            )
        except Exception as exc:
            self.warnings.append(str(exc))
            if self.problem is None:
                self.problem = _fallback_problem(self.problem_text, str(exc))
            yield self._progress(
                PipelineState.DONE,
                "Pipeline gap loi: " + str(exc) + ". Tra ket qua tam thoi.",
                100,
                self._all_cases(),
            )

    def _quality_gate(self, expected_count: int) -> None:
        if expected_count <= 0:
            return
        if not self.killer_cases:
            self.warnings.append("Khong co KILLER cases de kiem tra do kho.")
            self.killer_cases = self._promote_medium_to_killer(expected_count)
            return

        valid_cases = [
            case for case in self.killer_cases if self._is_valid_killer(case.input, case.description)
        ]
        if len(valid_cases) < len(self.killer_cases):
            self.warnings.append(
                f"Quality gate loai {len(self.killer_cases) - len(valid_cases)} KILLER cases qua yeu."
            )
            self.killer_cases = valid_cases

        small_tokens = _average_token_count(self.small_cases)
        killer_tokens = _average_token_count(self.killer_cases)
        if killer_tokens <= max(20.0, small_tokens * 1.5):
            self.warnings.append(
                "KILLER cases co ve qua nho so voi SMALL cases; da thu regenerate voi profile KILLER."
            )
            regenerated = self._safe_generate_cases(
                len(self.killer_cases),
                "KILLER",
                _inputs_of(self._all_cases()),
            )
            if regenerated:
                self.killer_cases = [
                    case
                    for case in regenerated
                    if self._is_valid_killer(case.input, case.description)
                ] or regenerated

        if len(self.killer_cases) < expected_count:
            self.killer_cases = _merge_unique_cases(
                self.killer_cases,
                self._promote_medium_to_killer(expected_count),
            )[:expected_count]

    async def _generate_killer_parallel(self, count: int) -> list[TestCaseSchema]:
        if count <= 0 or self.problem is None:
            return []

        strategies = _killer_strategies_for_problem(self.problem)
        requested = max(count, min(len(strategies), count * 2))
        tasks = [
            asyncio.to_thread(
                generate_single_killer_case,
                self.problem,
                strategy,
                _inputs_of(self._all_cases()),
            )
            for strategy in strategies[:requested]
        ]
        results = await asyncio.gather(*tasks, return_exceptions=True)
        cases = [
            result
            for result in results
            if isinstance(result, TestCaseSchema) and result.input
        ]

        if len(cases) < count:
            fallback = await asyncio.to_thread(
                generate_testcases,
                self.problem,
                count - len(cases),
                self.request.include_edge_cases,
                "KILLER",
                _inputs_of(self._all_cases() + cases),
            )
            cases.extend(_cases_from_response(fallback))

        return _merge_unique_cases(cases)

    def _is_valid_killer(self, tc_input: str, description: str = "") -> bool:
        if not tc_input or not tc_input.strip():
            return False

        token_count = len(tc_input.split())
        small_tokens = _average_token_count(self.small_cases)
        if small_tokens > 0 and token_count <= max(20.0, small_tokens * 1.5):
            return False

        max_n = _extract_max_n(self.problem.constraints if self.problem else [])
        if 0 < max_n <= 200000 and token_count < max_n * 0.6:
            desc = (description or "").lower()
            if not any(
                marker in desc
                for marker in ("boundary", "upper", "lower", "impossible", "single")
            ):
                return False

        numbers = _extract_numbers(tc_input)
        if max_n > 100 and numbers and max(abs(value) for value in numbers) < max_n * 0.5:
            if token_count < max(30, min(max_n, 200000) * 0.2):
                return False

        return True

    def _promote_medium_to_killer(self, count: int) -> list[TestCaseSchema]:
        promoted: list[TestCaseSchema] = []
        for case in self.medium_cases:
            if len(promoted) >= count:
                break
            if not case or not case.input:
                continue
            promoted.append(
                TestCaseSchema(
                    id=case.id,
                    input=case.input,
                    expected_output=case.expected_output,
                    description="[KILLER] promoted MEDIUM fallback - " + (case.description or ""),
                    is_edge_case=True,
                )
            )

        if len(promoted) < count:
            generated = self._safe_generate_cases(
                count - len(promoted),
                "KILLER",
                _inputs_of(self._all_cases() + promoted),
            )
            promoted = _merge_unique_cases(promoted, generated)

        return promoted[:count]

    def _safe_generate_cases(
        self,
        count: int,
        profile: str,
        existing_inputs: list[str],
    ) -> list[TestCaseSchema]:
        if count <= 0 or self.problem is None:
            return []
        try:
            return _cases_from_response(
                generate_testcases(
                    self.problem,
                    count,
                    self.request.include_edge_cases,
                    profile,
                    existing_inputs,
                )
            )
        except Exception as exc:
            self.warnings.append(f"{profile} generation failed: {exc}")
            return []

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


def _merge_unique_cases(*groups: list[TestCaseSchema]) -> list[TestCaseSchema]:
    merged: list[TestCaseSchema] = []
    seen: set[str] = set()
    for group in groups:
        for case in group or []:
            if not case or not case.input:
                continue
            key = _input_key(case.input)
            if not key or key in seen:
                continue
            merged.append(case)
            seen.add(key)
    return merged


def _input_key(value: str) -> str:
    normalized = (value or "").replace("\r\n", "\n").replace("\r", "\n")
    normalized = "\n".join(line.rstrip() for line in normalized.split("\n"))
    return normalized.strip()


def _average_token_count(cases: list[TestCaseSchema]) -> float:
    if not cases:
        return 0.0
    counts = [len((case.input or "").split()) for case in cases]
    return sum(counts) / len(counts)


def _killer_strategies_for_problem(problem: ProblemSchema) -> list[str]:
    problem_type = problem.problem_type or "ARRAY_SEQUENCE"
    strategies = KILLER_STRATEGIES.get(problem_type, KILLER_STRATEGIES["ARRAY_SEQUENCE"])
    return list(dict.fromkeys(strategy for strategy in strategies if strategy))


def _extract_max_n(constraints: list[str]) -> int:
    best = 0
    for constraint in constraints or []:
        text = str(constraint).replace(",", "")
        value_pattern = r"((?:\d+\s*(?:\*|x|×|⋅|·)\s*)?10\s*\^\s*\d+|\d+(?:e\d+)?)"
        for pattern in (
            rf"\bn\s*(?:<=|≤|<)\s*{value_pattern}",
            rf"\bN\s*(?:<=|≤|<)\s*{value_pattern}",
            rf"1\s*(?:<=|≤)\s*n\s*(?:<=|≤)\s*{value_pattern}",
            rf"1\s*(?:<=|≤)\s*N\s*(?:<=|≤)\s*{value_pattern}",
        ):
            match = re.search(pattern, text, re.IGNORECASE)
            if match:
                best = max(best, _parse_bound_value(match.group(1)))
    return best


def _parse_bound_value(value: str) -> int:
    text = (value or "").replace(" ", "").replace(",", "").lower()
    text = text.replace("×", "*").replace("⋅", "*").replace("·", "*").replace("x", "*")
    try:
        if "e" in text:
            return int(float(text))
        power_match = re.fullmatch(r"(?:(\d+)\*)?10\^(\d+)", text)
        if power_match:
            factor = int(power_match.group(1) or "1")
            return factor * (10 ** int(power_match.group(2)))
        return int(text)
    except Exception:
        return 0


def _extract_numbers(value: str) -> list[int]:
    numbers: list[int] = []
    for match in re.finditer(r"-?\d+", value or ""):
        try:
            numbers.append(int(match.group(0)))
        except ValueError:
            continue
    return numbers


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


def _fallback_problem(text: str, reason: str) -> ProblemSchema:
    return ProblemSchema(
        title="Problem (unparsed)",
        description=text or reason or "Pipeline could not parse the problem statement.",
        input_format="",
        output_format="",
        constraints=[],
        sample_inputs=[],
        sample_outputs=[],
        problem_type="UNKNOWN",
        secondary_type="",
        type_confidence=0.0,
        tle_strategy="",
    )


class TestPyramidPlan:
    def __init__(self, small_count: int, medium_count: int, killer_count: int):
        self.small_count = small_count
        self.medium_count = medium_count
        self.killer_count = killer_count
