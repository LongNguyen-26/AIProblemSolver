from models.schemas import ProblemSchema, TestCaseSchema


REQUIRED_TESTCASE_TAXONOMY = {
    "sample": {
        "description": "Official sample cases from the statement",
        "min_count": 1,
        "profiles": ["SMALL"],
    },
    "boundary": {
        "description": "Boundary values such as n=1, n=MAX, min/max values",
        "min_count": 2,
        "profiles": ["SMALL"],
    },
    "edge_structural": {
        "description": "Special structures such as empty/single/all-equal/sorted cases",
        "min_count": 2,
        "profiles": ["SMALL"],
    },
    "adversarial": {
        "description": "Inputs designed to break common wrong logic",
        "min_count": 3,
        "profiles": ["MEDIUM", "SMALL"],
    },
    "stress_random": {
        "description": "Random valid smoke tests",
        "min_count": 2,
        "profiles": ["SMALL"],
    },
    "performance": {
        "description": "Maximum-size inputs for performance/TLE testing",
        "min_count": 3,
        "profiles": ["KILLER"],
    },
}


class TestcaseCoverageChecker:
    def check_coverage(
        self,
        testcases: list[TestCaseSchema],
        problem: ProblemSchema | None = None,
    ) -> dict:
        coverage = {category: 0 for category in REQUIRED_TESTCASE_TAXONOMY}

        for testcase in testcases or []:
            category = self.infer_category(testcase)
            coverage[category] = coverage.get(category, 0) + 1

        missing = []
        for category, info in REQUIRED_TESTCASE_TAXONOMY.items():
            required = int(info["min_count"])
            have = coverage.get(category, 0)
            if have < required:
                missing.append(
                    {
                        "category": category,
                        "have": have,
                        "need": required,
                        "profiles": list(info["profiles"]),
                    }
                )

        return {
            "coverage": coverage,
            "missing": missing,
            "complete": len(missing) == 0,
        }

    def infer_category(self, testcase: TestCaseSchema | None) -> str:
        if testcase is None:
            return "adversarial"
        desc = (testcase.description or "").lower()

        if "sample" in desc or "official" in desc:
            return "sample"
        if (
            "performance" in desc
            or "killer" in desc
            or "[killer]" in desc
            or "maximum-size" in desc
            or "max-size" in desc
        ):
            return "performance"
        if (
            "boundary" in desc
            or "minimum" in desc
            or "maximum" in desc
            or "n=1" in desc
            or "n=max" in desc
            or "min/max" in desc
        ):
            return "boundary"
        if (
            "edge_structural" in desc
            or "structural" in desc
            or "edge" in desc
            or "all equal" in desc
            or "all-equal" in desc
            or "empty" in desc
            or "single" in desc
            or "sorted" in desc
            or "star" in desc
            or "chain" in desc
            or "bamboo" in desc
        ):
            return "edge_structural"
        if "stress_random" in desc or "random" in desc or "smoke" in desc:
            return "stress_random"
        return "adversarial"
