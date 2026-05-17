import random
import subprocess
import sys
import tempfile
from dataclasses import dataclass
from pathlib import Path

from models.schemas import ProblemSchema, StressResult, TestCaseSchema
from services.code_generator import fix_code, generate_code, generate_input_generator


DEFAULT_ORACLE_RETRIES = 3
DEFAULT_GENERATOR_RETRIES = 2
MAX_EXECUTION_OUTPUT_CHARS = 200000
MAX_GENERATED_INPUT_CHARS = 200000


@dataclass
class ExecutionResult:
    stdout: str
    stderr: str
    exit_code: int
    timed_out: bool = False


@dataclass
class VerifiedCode:
    code: str
    trusted: bool
    retries: int
    message: str = ""


class PythonExecutor:
    def compile(self, code: str, timeout: int = 10) -> str:
        with tempfile.TemporaryDirectory(prefix="aips_compile_") as directory:
            path = Path(directory) / "Main.py"
            path.write_text(code or "", encoding="utf-8")
            result = self._run_process(
                [sys.executable, "-m", "py_compile", str(path)],
                "",
                timeout,
                directory,
            )
            if result.timed_out:
                return "Compile check timed out."
            if result.exit_code != 0:
                return _non_blank(result.stderr, result.stdout)
            return ""

    def run(self, code: str, stdin_data: str, timeout: int = 5) -> ExecutionResult:
        with tempfile.TemporaryDirectory(prefix="aips_run_") as directory:
            path = Path(directory) / "Main.py"
            path.write_text(code or "", encoding="utf-8")
            return self._run_process(
                [sys.executable, str(path)],
                stdin_data or "",
                timeout,
                directory,
            )

    def _run_process(
        self,
        command: list[str],
        stdin_data: str,
        timeout: int,
        cwd: str,
    ) -> ExecutionResult:
        try:
            completed = subprocess.run(
                command,
                input=stdin_data,
                text=True,
                encoding="utf-8",
                errors="replace",
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                timeout=timeout,
                cwd=cwd,
            )
            return ExecutionResult(
                stdout=_clip(completed.stdout),
                stderr=_clip(completed.stderr),
                exit_code=completed.returncode,
            )
        except subprocess.TimeoutExpired as exc:
            return ExecutionResult(
                stdout=_clip(exc.stdout or ""),
                stderr=_clip(exc.stderr or "Time limit exceeded"),
                exit_code=-1,
                timed_out=True,
            )
        except Exception as exc:
            return ExecutionResult("", str(exc), -1)


class StressTestingAgent:
    def __init__(self, executor: PythonExecutor | None = None):
        self.executor = executor or PythonExecutor()
        self.last_brute_code = ""
        self.rounds_completed = 0

    def run(
        self,
        problem: ProblemSchema,
        small_cases: list[TestCaseSchema],
        rounds: int = 30,
    ) -> StressResult:
        problem_type = "general"
        try:
            normalized_rounds = max(1, min(rounds or 30, 100))
            usable_small_cases = _usable_small_cases(small_cases)
            if not usable_small_cases:
                return StressResult(
                    trusted=False,
                    rounds_completed=0,
                    message="No small cases with expected output were provided.",
                    problem_type=_classify_problem(problem),
                )

            problem_type = _classify_problem(problem)
            brute = self._get_verified_oracle(
                problem,
                "BRUTE",
                usable_small_cases,
                DEFAULT_ORACLE_RETRIES,
            )
            if brute.code:
                self.last_brute_code = brute.code
            if not brute.trusted:
                return StressResult(
                    trusted=False,
                    trusted_oracle_code=self.last_brute_code,
                    trusted_oracle_language="python",
                    rounds_completed=0,
                    oracle_retries=brute.retries,
                    message="Brute oracle is not trusted: " + brute.message,
                    problem_type=problem_type,
                )

            optimized = self._get_verified_optimized_oracle(
                problem,
                usable_small_cases,
                DEFAULT_ORACLE_RETRIES,
            )
            oracle_retries = brute.retries + optimized.retries
            if not optimized.trusted:
                return StressResult(
                    trusted=False,
                    trusted_oracle_code=brute.code,
                    trusted_oracle_language="python",
                    rounds_completed=0,
                    oracle_retries=oracle_retries,
                    message=(
                        "Optimized oracle is not trusted: "
                        + optimized.message
                        + ". Dung brute oracle tam thoi."
                    ),
                    problem_type=problem_type,
                )

            generator = self._get_verified_generator(
                problem,
                "SMALL",
                DEFAULT_GENERATOR_RETRIES,
            )
            if not generator.trusted:
                return StressResult(
                    trusted=False,
                    trusted_oracle_code=optimized.code,
                    trusted_oracle_language="python",
                    rounds_completed=0,
                    oracle_retries=oracle_retries,
                    generator_trusted=False,
                    message="Input generator is not trusted: " + generator.message,
                    problem_type=problem_type,
                )

            stress = self._run_stress_loop(
                brute.code,
                optimized.code,
                generator.code,
                normalized_rounds,
            )
            found_counterexample = bool(stress.get("found_counterexample"))
            self.rounds_completed = int(stress.get("rounds_completed", 0))
            return StressResult(
                trusted=not found_counterexample,
                trusted_oracle_code=optimized.code if not found_counterexample else brute.code,
                trusted_oracle_language="python",
                found_counterexample=found_counterexample,
                counterexample_input=str(stress.get("input", "")),
                brute_output=str(stress.get("brute_output", "")),
                optimized_output=str(stress.get("optimized_output", "")),
                rounds_completed=self.rounds_completed,
                oracle_retries=oracle_retries,
                generator_trusted=True,
                message=str(stress.get("message", "Stress completed.")),
                problem_type=problem_type,
            )
        except Exception as exc:
            return StressResult(
                trusted=False,
                trusted_oracle_code=self.last_brute_code,
                trusted_oracle_language="python",
                rounds_completed=self.rounds_completed,
                found_counterexample=False,
                generator_trusted=False,
                message=(
                    f"Stress agent gap loi: {exc}. "
                    "Dung brute oracle tam thoi neu co."
                ),
                problem_type=problem_type,
            )

    def _get_verified_optimized_oracle(
        self,
        problem: ProblemSchema,
        small_cases: list[TestCaseSchema],
        max_retries: int,
    ) -> VerifiedCode:
        first = self._get_verified_oracle(problem, "AC", small_cases, max_retries)
        if first.trusted:
            return first

        alternate = self._get_verified_oracle(
            problem,
            "OPTIMIZED_ALT",
            small_cases,
            max_retries,
        )
        return VerifiedCode(
            code=alternate.code,
            trusted=alternate.trusted,
            retries=first.retries + alternate.retries,
            message=alternate.message or first.message,
        )

    def _get_verified_oracle(
        self,
        problem: ProblemSchema,
        oracle_type: str,
        small_cases: list[TestCaseSchema],
        max_retries: int,
    ) -> VerifiedCode:
        code = generate_code(problem, oracle_type, "python", small_cases).code
        retries = 0
        last_message = ""

        for _ in range(max_retries):
            compile_error = self.executor.compile(code, timeout=10)
            if compile_error:
                last_message = "Compile error:\n" + compile_error
                code = fix_code(problem, code, last_message, oracle_type, "python")
                retries += 1
                continue

            wrong_case, actual = self._find_wrong_case(code, small_cases)
            if wrong_case is None:
                return VerifiedCode(code=code, trusted=True, retries=retries)

            last_message = (
                "Wrong answer on input:\n"
                f"{wrong_case.input}\n"
                "Expected:\n"
                f"{wrong_case.expected_output}\n"
                "Got:\n"
                f"{actual}\n"
                "Fix the logic."
            )
            code = fix_code(problem, code, last_message, oracle_type, "python")
            retries += 1

        return VerifiedCode(code=code, trusted=False, retries=retries, message=last_message)

    def _find_wrong_case(
        self,
        code: str,
        small_cases: list[TestCaseSchema],
    ) -> tuple[TestCaseSchema | None, str]:
        for case in small_cases:
            result = self.executor.run(code, case.input, timeout=10)
            if result.timed_out:
                return case, "Time limit exceeded"
            if result.exit_code != 0:
                return case, _non_blank(result.stderr, result.stdout)
            if _normalize_output(result.stdout) != _normalize_output(case.expected_output):
                return case, result.stdout
        return None, ""

    def _get_verified_generator(
        self,
        problem: ProblemSchema,
        profile: str,
        max_retries: int,
    ) -> VerifiedCode:
        code = generate_input_generator(problem, profile)
        retries = 0
        last_message = ""

        for _ in range(max_retries):
            last_message = ""
            compile_error = self.executor.compile(code, timeout=10)
            if compile_error:
                last_message = "Generator compile error:\n" + compile_error
                code = fix_code(problem, code, last_message, "GENERATOR", "python")
                retries += 1
                continue

            samples: list[str] = []
            for seed in range(3):
                result = self.executor.run(code, str(seed), timeout=5)
                if result.timed_out:
                    last_message = f"Generator timed out for seed {seed}."
                    break
                if result.exit_code != 0:
                    last_message = (
                        f"Generator crashed for seed {seed}:\n"
                        + _non_blank(result.stderr, result.stdout)
                    )
                    break
                samples.append(result.stdout)

            if last_message:
                code = fix_code(problem, code, last_message, "GENERATOR", "python")
                retries += 1
                continue

            format_error = _validate_input_format(samples)
            if format_error:
                last_message = "Generated input fails format check:\n" + format_error
                code = fix_code(problem, code, last_message, "GENERATOR", "python")
                retries += 1
                continue

            return VerifiedCode(code=code, trusted=True, retries=retries)

        return VerifiedCode(
            code=code,
            trusted=False,
            retries=retries,
            message=last_message or "Generator did not pass validation.",
        )

    def _run_stress_loop(
        self,
        brute_code: str,
        optimized_code: str,
        generator_code: str,
        rounds: int,
    ) -> dict:
        completed = 0
        for index in range(rounds):
            seed = str(random.randint(0, 10**9))
            generated = self.executor.run(generator_code, seed, timeout=3)
            if generated.timed_out or generated.exit_code != 0:
                continue
            if _validate_input_format([generated.stdout]):
                continue

            brute = self.executor.run(brute_code, generated.stdout, timeout=10)
            optimized = self.executor.run(optimized_code, generated.stdout, timeout=10)
            if brute.timed_out or brute.exit_code != 0:
                continue
            if optimized.timed_out or optimized.exit_code != 0:
                return {
                    "found_counterexample": True,
                    "input": generated.stdout,
                    "brute_output": brute.stdout,
                    "optimized_output": _non_blank(optimized.stderr, optimized.stdout),
                    "rounds_completed": completed,
                    "message": f"Optimized oracle failed on stress round {index + 1}.",
                }

            completed += 1
            if _normalize_output(brute.stdout) != _normalize_output(optimized.stdout):
                return {
                    "found_counterexample": True,
                    "input": generated.stdout,
                    "brute_output": brute.stdout,
                    "optimized_output": optimized.stdout,
                    "rounds_completed": completed,
                    "message": f"Mismatch found on stress round {index + 1}.",
                }

        return {
            "found_counterexample": False,
            "rounds_completed": completed,
            "message": f"No mismatch after {completed} valid stress rounds.",
        }


def _usable_small_cases(small_cases: list[TestCaseSchema]) -> list[TestCaseSchema]:
    usable = []
    for case in small_cases or []:
        if not case or not (case.input or "").strip():
            continue
        if not (case.expected_output or "").strip():
            continue
        usable.append(case)
    return usable


def _validate_input_format(samples: list[str]) -> str:
    for index, sample in enumerate(samples or [], start=1):
        value = sample or ""
        if not value.strip():
            return f"Sample #{index} is empty."
        if len(value) > MAX_GENERATED_INPUT_CHARS:
            return f"Sample #{index} exceeds {MAX_GENERATED_INPUT_CHARS} characters."
        lowered = value.lower()
        if "```" in value or "expected output" in lowered or "\noutput:" in lowered:
            return f"Sample #{index} contains non-input text."
    return ""


def _classify_problem(problem: ProblemSchema) -> str:
    text = " ".join(
        [
            problem.title or "",
            problem.description or "",
            problem.input_format or "",
            problem.output_format or "",
            " ".join(problem.constraints or []),
        ]
    ).lower()
    categories = [
        ("graph", ["tree", "graph", "edge", "vertex", "path", "connected"]),
        ("dynamic_programming", ["subsequence", "dp", "ways", "minimum cost", "maximum score"]),
        ("string", ["string", "substring", "subsequence", "palindrome", "character"]),
        ("math", ["prime", "gcd", "modulo", "integer", "number theory"]),
        ("geometry", ["point", "line", "circle", "polygon", "area"]),
        ("data_structure", ["query", "update", "range", "segment", "fenwick"]),
    ]
    for category, keywords in categories:
        if any(keyword in text for keyword in keywords):
            return category
    return "general"


def _normalize_output(value: str) -> str:
    return "\n".join(line.rstrip() for line in (value or "").splitlines()).rstrip()


def _non_blank(preferred: str, fallback: str) -> str:
    return preferred if preferred and preferred.strip() else fallback


def _clip(value: str) -> str:
    if isinstance(value, bytes):
        text = value.decode("utf-8", errors="replace")
    else:
        text = value or ""
    if len(text) <= MAX_EXECUTION_OUTPUT_CHARS:
        return text
    return text[:MAX_EXECUTION_OUTPUT_CHARS] + "\n...[truncated]"
