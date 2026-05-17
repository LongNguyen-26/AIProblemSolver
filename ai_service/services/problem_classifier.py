from typing import Any

from models.schemas import ProblemClassification, ProblemSchema
from services.groq_json import request_json
from services.problem_taxonomy import DEFAULT_PROBLEM_TYPE, PROBLEM_TYPES


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
    problem.problem_type = classification.primary_type
    problem.secondary_type = classification.secondary_type or ""
    problem.type_confidence = classification.confidence
    problem.tle_strategy = _taxonomy_value(classification.primary_type, "killer_strategy")
    return problem


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


def _float_value(value: Any, fallback: float) -> float:
    try:
        numeric = float(value)
    except Exception:
        return fallback
    return max(0.0, min(1.0, numeric))
