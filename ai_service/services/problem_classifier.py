import re
from typing import Any, Optional

from models.schemas import ProblemClassification, ProblemSchema
from services.groq_json import request_json
from services.problem_taxonomy import DEFAULT_PROBLEM_TYPE, PROBLEM_TYPES

SMALL_N_THRESHOLD = 1000
SMALL_N_PROBLEM_TYPES = {"MATH_FORMULA", "STRING_BASIC", "AD_HOC_SIMPLE"}


CLASSIFIER_PROMPT = """
Classify this competitive programming problem into one of the available types.

Available types:
ARRAY_SEQUENCE, GRAPH_UNDIRECTED, GRAPH_DIRECTED, TREE, DP_1D, DP_2D, DP_BITMASK,
MATH_NUMBER_THEORY, MATH_COMBINATORICS, STRING, GEOMETRY, GREEDY, BINARY_SEARCH,
DATA_STRUCTURE, CONSTRUCTIVE, GAME_THEORY

Return ONLY valid JSON:
{
  "primary_type": "TREE",
  "secondary_type": "DP_1D",
  "confidence": 0.9,
  "reasoning": "The problem involves a rooted tree with subtree DP."
}

Problem title: {title}
Problem description: {description}
Input format: {input_format}
Output format: {output_format}
Constraints: {constraints}
"""


def classify_problem(problem: ProblemSchema) -> ProblemClassification:
    keyword_classification = _keyword_classification(problem)
    try:
        prompt = (
            CLASSIFIER_PROMPT
            .replace("{title}", problem.title or "")
            .replace("{description}", (problem.description or "")[:2000])
            .replace("{input_format}", problem.input_format or "")
            .replace("{output_format}", problem.output_format or "")
            .replace("{constraints}", "; ".join(problem.constraints or []))
        )
        data = request_json([{"role": "user", "content": prompt}])
        classification = _normalize_classification(data, keyword_classification)
    except Exception:
        classification = keyword_classification

    if classification.confidence < 0.6:
        return keyword_classification
    return classification


def classify_problem_fast(problem: ProblemSchema) -> ProblemClassification:
    if problem.problem_type:
        return ProblemClassification(
            primary_type=problem.problem_type,
            secondary_type=problem.secondary_type or "",
            confidence=problem.type_confidence or 0.75,
            reasoning="Problem already contains classification metadata.",
        )
    return _keyword_classification(problem)


def apply_classification(problem: ProblemSchema) -> ProblemSchema:
    classification = classify_problem(problem)
    return _apply_classification_result(problem, classification)


def apply_keyword_classification(problem: ProblemSchema) -> ProblemSchema:
    return _apply_classification_result(problem, _keyword_classification(problem))


def _apply_classification_result(
    problem: ProblemSchema,
    classification: ProblemClassification,
) -> ProblemSchema:
    problem.problem_type = classification.primary_type
    problem.secondary_type = classification.secondary_type or ""
    problem.type_confidence = classification.confidence
    problem.tle_strategy = _taxonomy_value(classification.primary_type, "killer_strategy")
    return apply_tle_relevance_metadata(problem)


def apply_tle_relevance_metadata(problem: ProblemSchema) -> ProblemSchema:
    max_n = extract_max_n(problem.constraints or [])
    problem.max_constraint_n = max_n
    problem.is_small_n = (
        (max_n is not None and max_n < SMALL_N_THRESHOLD)
        or _is_small_n_problem_type(problem.problem_type)
    )
    return problem


def extract_max_n(constraints_text: str | list[str]) -> Optional[int]:
    if isinstance(constraints_text, list):
        text = "\n".join(str(item) for item in constraints_text if item is not None)
    else:
        text = str(constraints_text or "")
    if not text.strip():
        return None

    cleaned = re.sub(r"(?<=\d)[ \t,_]+(?=\d)", "", text)
    value_pattern = (
        r"((?:\d+\s*(?:\*|x)\s*)?10\s*\^\s*\d+|"
        r"\d+(?:\.\d+)?(?:e\d+)?)"
    )
    relation = r"(?:<=|<|\\leq|\u2264)"
    variable = (
        r"(?:n|m|q|k|t|p|r|c|h|w|row|rows|col|cols|column|columns|"
        r"length|len|size|vertices|edges)"
    )
    patterns = [
        rf"\b{variable}\b\s*{relation}\s*{value_pattern}",
        rf"\d+\s*{relation}\s*\b{variable}\b\s*{relation}\s*{value_pattern}",
        rf"\b{variable}\b\s*(?:is\s*)?(?:at\s+most|up\s+to)\s*{value_pattern}",
    ]

    candidates: list[int] = []
    for pattern in patterns:
        for match in re.finditer(pattern, cleaned, re.IGNORECASE):
            value = _parse_bound_value(match.group(match.lastindex or 1))
            if value > 0:
                candidates.append(value)
    return max(candidates) if candidates else None


def _keyword_classification(problem: ProblemSchema) -> ProblemClassification:
    text = _problem_text(problem)
    scores: dict[str, int] = {}
    for problem_type, info in PROBLEM_TYPES.items():
        score = 0
        for keyword in info["keywords"]:
            if keyword.lower() in text:
                score += 1
        scores[problem_type] = score

    ordered = sorted(scores.items(), key=lambda item: item[1], reverse=True)
    primary, primary_score = ordered[0] if ordered else (DEFAULT_PROBLEM_TYPE, 0)
    secondary = ""
    if len(ordered) > 1 and ordered[1][1] > 0:
        secondary = ordered[1][0]

    if primary_score <= 0:
        confidence = 0.35
        primary = DEFAULT_PROBLEM_TYPE
    else:
        confidence = min(0.9, 0.55 + primary_score * 0.12)

    return ProblemClassification(
        primary_type=primary,
        secondary_type=secondary,
        confidence=confidence,
        reasoning=f"Keyword classifier matched {primary_score} taxonomy keywords.",
    )


def _normalize_classification(
    data: dict[str, Any],
    fallback: ProblemClassification,
) -> ProblemClassification:
    if not isinstance(data, dict):
        return fallback

    primary = str(data.get("primary_type") or fallback.primary_type).strip().upper()
    secondary = str(data.get("secondary_type") or "").strip().upper()
    if primary not in PROBLEM_TYPES:
        primary = fallback.primary_type
    if secondary and secondary not in PROBLEM_TYPES:
        secondary = ""

    return ProblemClassification(
        primary_type=primary,
        secondary_type=secondary,
        confidence=_float_value(data.get("confidence"), fallback.confidence),
        reasoning=str(data.get("reasoning") or fallback.reasoning),
    )


def _problem_text(problem: ProblemSchema) -> str:
    return " ".join(
        [
            problem.title or "",
            problem.description or "",
            problem.input_format or "",
            problem.output_format or "",
            " ".join(problem.constraints or []),
        ]
    ).lower()


def _taxonomy_value(problem_type: str, key: str) -> str:
    info = PROBLEM_TYPES.get(problem_type or "")
    if not info:
        return ""
    value = info.get(key, "")
    return value if isinstance(value, str) else ""


def _is_small_n_problem_type(problem_type: str) -> bool:
    return (problem_type or "").strip().upper() in SMALL_N_PROBLEM_TYPES


def _parse_bound_value(value: str) -> int:
    text = (value or "").replace(" ", "").replace(",", "").replace("_", "").lower()
    text = text.replace("x", "*")
    try:
        if "e" in text:
            return int(float(text))
        power_match = re.fullmatch(r"(?:(\d+)\*)?10\^(\d+)", text)
        if power_match:
            factor = int(power_match.group(1) or "1")
            return factor * (10 ** int(power_match.group(2)))
        return int(float(text))
    except Exception:
        return 0


def _float_value(value: Any, fallback: float) -> float:
    try:
        numeric = float(value)
    except Exception:
        return fallback
    return max(0.0, min(1.0, numeric))
