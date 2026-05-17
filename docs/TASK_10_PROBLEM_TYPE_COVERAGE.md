# TASK 10 – Bao Quát Dạng Bài CP/ICPC: Problem Type Classifier

## Vấn đề

- Prompt sinh testcase hiện tại là generic → không biết cần sinh graph input, tree input, hay geometry input.
- Không có chiến lược edge case riêng cho từng dạng bài (ví dụ: graph cần test đồ thị rỗng, cây, cycle; DP cần test N=1, N=max).
- Dẫn đến test case yếu, không bắt được bug đặc thù của từng dạng.

## Mục tiêu

- Tự động phân loại bài vào **problem type** với độ tin cậy cao.
- Mỗi problem type có **chiến lược sinh testcase riêng** (edge cases đặc thù + killer tests).
- Hỗ trợ ít nhất 15 dạng bài phổ biến trong ICPC/Codeforces.

---

## Bước 1: Định nghĩa taxonomy dạng bài

File mới: `ai_service/services/problem_taxonomy.py`

```python
PROBLEM_TYPES = {
    "ARRAY_SEQUENCE": {
        "keywords": ["array", "sequence", "subarray", "prefix sum", "sliding window"],
        "edge_cases": [
            "n=1 (single element)",
            "all elements equal",
            "already sorted / reverse sorted",
            "all zeros or all max values",
            "n=max_n (stress)",
        ],
        "killer_strategy": "n=max, adversarial for sorting or prefix-sum overflow",
        "input_generator_hint": "Generate array of n integers within constraint range",
    },
    "GRAPH_UNDIRECTED": {
        "keywords": ["graph", "vertices", "edges", "connected", "path", "cycle"],
        "edge_cases": [
            "n=1 (single node, no edges)",
            "disconnected graph",
            "complete graph K_n",
            "graph with self-loops (if allowed)",
            "tree (n-1 edges)",
            "n=max, m=max (dense)",
        ],
        "killer_strategy": "Chain graph (path) or star graph to stress DFS/BFS stack depth",
        "input_generator_hint": "Generate random graph with n nodes and m edges",
    },
    "GRAPH_DIRECTED": {
        "keywords": ["directed", "DAG", "topological", "SCC", "tournament"],
        "edge_cases": [
            "DAG with single source",
            "graph with cycles",
            "single node self-loop",
            "n=max directed path (chain)",
        ],
        "killer_strategy": "Long directed chain to stress topological sort or DFS",
        "input_generator_hint": "Generate random directed graph",
    },
    "TREE": {
        "keywords": ["tree", "rooted tree", "parent", "children", "LCA", "subtree"],
        "edge_cases": [
            "n=1",
            "linear chain (bamboo)",
            "star tree (one root, all leaves)",
            "balanced binary tree",
            "n=max bamboo (stress recursion depth)",
        ],
        "killer_strategy": "Bamboo tree of n=max to force O(n) per query or stack overflow",
        "input_generator_hint": "Generate random tree with n nodes",
    },
    "DP_1D": {
        "keywords": ["dp", "dynamic programming", "maximize", "minimize", "count ways", "knapsack"],
        "edge_cases": [
            "n=1",
            "n=max",
            "all items identical",
            "capacity=0 or capacity=max",
            "answer is 0 (impossible)",
        ],
        "killer_strategy": "n=max with adversarial item sizes to stress memory/time",
        "input_generator_hint": "Generate n items within weight and value constraints",
    },
    "DP_2D": {
        "keywords": ["grid", "matrix", "2D dp", "paths in grid", "LCS", "edit distance"],
        "edge_cases": [
            "1x1 grid",
            "1xN or Nx1 grid",
            "grid fully blocked",
            "n=m=max",
        ],
        "killer_strategy": "n=m=max grid",
        "input_generator_hint": "Generate n x m grid with chars or integers",
    },
    "DP_BITMASK": {
        "keywords": ["bitmask", "subset", "TSP", "assignment", "permutation dp"],
        "edge_cases": [
            "n=1",
            "n=max (usually 20-25)",
            "all costs equal",
            "impossible assignment",
        ],
        "killer_strategy": "n=20 or n=25 (max allowed), random costs",
        "input_generator_hint": "Generate n x n cost matrix",
    },
    "MATH_NUMBER_THEORY": {
        "keywords": ["prime", "gcd", "lcm", "modulo", "factorial", "divisors", "euler"],
        "edge_cases": [
            "n=1",
            "n=prime (large)",
            "n=1 (answer could be 0 or 1)",
            "n=max (overflow check)",
        ],
        "killer_strategy": "n=max, or n=large_prime",
        "input_generator_hint": "Generate integers within constraint range, include primes",
    },
    "MATH_COMBINATORICS": {
        "keywords": ["combination", "permutation", "binomial", "choose", "ways"],
        "edge_cases": [
            "n=0 or k=0",
            "n=k (choose all)",
            "n=max, k=n/2 (largest binomial)",
            "answer requires modular arithmetic",
        ],
        "killer_strategy": "n=max, k=n/2",
        "input_generator_hint": "Generate n and k within constraint",
    },
    "STRING": {
        "keywords": ["string", "substring", "palindrome", "prefix", "suffix", "pattern"],
        "edge_cases": [
            "length=1",
            "all same characters (aaa...)",
            "alternating characters (ababab...)",
            "length=max",
            "empty string (if allowed)",
        ],
        "killer_strategy": "String of length=max, all same char (worst case for KMP/hashing)",
        "input_generator_hint": "Generate string of length n over alphabet {a..z}",
    },
    "GEOMETRY": {
        "keywords": ["points", "polygon", "convex hull", "distance", "area", "intersection"],
        "edge_cases": [
            "all points collinear",
            "n=1 or n=2",
            "coincident points",
            "all points on convex hull",
            "n=max",
        ],
        "killer_strategy": "n=max points on a circle (all on convex hull)",
        "input_generator_hint": "Generate n points with coordinates in constraint range",
    },
    "GREEDY": {
        "keywords": ["greedy", "minimum cost", "maximum profit", "schedule", "interval"],
        "edge_cases": [
            "n=1",
            "all intervals same",
            "all intervals overlap",
            "no overlap at all",
            "n=max",
        ],
        "killer_strategy": "n=max, adversarial interval arrangement",
        "input_generator_hint": "Generate intervals or tasks within constraint",
    },
    "BINARY_SEARCH": {
        "keywords": ["binary search", "sorted", "find minimum/maximum", "feasible", "monotone"],
        "edge_cases": [
            "answer is at boundary (lo or hi)",
            "all elements same",
            "n=1",
            "n=max",
        ],
        "killer_strategy": "n=max, answer at extreme boundary",
        "input_generator_hint": "Generate sorted array or feasibility check parameters",
    },
    "DATA_STRUCTURE": {
        "keywords": ["segment tree", "BIT", "Fenwick", "range query", "update", "heap"],
        "edge_cases": [
            "n=1",
            "all queries on same index",
            "alternating update/query",
            "n=max, q=max",
        ],
        "killer_strategy": "n=q=max, alternating updates and full-range queries",
        "input_generator_hint": "Generate n elements and q operations",
    },
    "CONSTRUCTIVE": {
        "keywords": ["construct", "build", "find any", "output any valid", "permutation"],
        "edge_cases": [
            "n=1 (trivial construction)",
            "n=2 (minimal non-trivial)",
            "n=max",
            "impossible case (if applicable)",
        ],
        "killer_strategy": "n=max with tight constraints",
        "input_generator_hint": "Generate valid input satisfying constructive constraints",
    },
    "GAME_THEORY": {
        "keywords": ["game", "Grundy", "Nim", "optimal play", "first player", "second player"],
        "edge_cases": [
            "trivially winning/losing position",
            "n=1",
            "symmetric position",
            "n=max",
        ],
        "killer_strategy": "n=max, position where answer is non-obvious",
        "input_generator_hint": "Generate game state within constraints",
    },
}
```

---

## Bước 2: Problem Type Classifier

File mới: `ai_service/services/problem_classifier.py`

```python
CLASSIFIER_PROMPT = """
Classify this competitive programming problem into one of the following types.
Return JSON with the primary type and confidence.

Available types:
ARRAY_SEQUENCE, GRAPH_UNDIRECTED, GRAPH_DIRECTED, TREE, DP_1D, DP_2D, DP_BITMASK,
MATH_NUMBER_THEORY, MATH_COMBINATORICS, STRING, GEOMETRY, GREEDY, BINARY_SEARCH,
DATA_STRUCTURE, CONSTRUCTIVE, GAME_THEORY

Return:
{
  "primary_type": "TREE",
  "secondary_type": "DP_1D",   // optional, if hybrid
  "confidence": 0.9,
  "reasoning": "The problem involves a rooted tree with subtree DP"
}

Problem title: {title}
Problem description: {description}
Constraints: {constraints}
"""

async def classify_problem(problem: ProblemSchema) -> ProblemClassification:
    # Keyword matching as fast pre-filter
    text = f"{problem.title} {problem.description} {problem.constraints}".lower()
    
    scores = {}
    for ptype, info in PROBLEM_TYPES.items():
        score = sum(1 for kw in info["keywords"] if kw in text)
        scores[ptype] = score

    best_keyword_match = max(scores, key=scores.get)
    
    # AI confirms or overrides
    result = await groq_client.request_json(
        CLASSIFIER_PROMPT.format(
            title=problem.title,
            description=problem.description[:500],
            constraints=problem.constraints,
        )
    )
    
    classification = ProblemClassification(**result)
    # Nếu AI confidence thấp, fallback về keyword match
    if classification.confidence < 0.6:
        classification.primary_type = best_keyword_match
    
    return classification
```

---

## Bước 3: Tích hợp vào testcase generator

File: `ai_service/services/testcase_generator.py`

```python
async def generate_testcases(problem, count, profile, include_edge_cases):
    # Classify problem type first
    classification = await classify_problem(problem)
    ptype = classification.primary_type
    strategy = PROBLEM_TYPES.get(ptype, PROBLEM_TYPES["ARRAY_SEQUENCE"])

    # Build type-aware prompt
    edge_case_hints = "\n".join(f"- {ec}" for ec in strategy["edge_cases"])
    killer_hint = strategy["killer_strategy"]
    gen_hint = strategy["input_generator_hint"]

    type_context = f"""
Problem type: {ptype}
Input generator approach: {gen_hint}

Edge cases to INCLUDE for SMALL profile:
{edge_case_hints}

Killer test strategy for KILLER profile:
{killer_hint}
"""
    # Tambah context này vào prompt gốc
    ...
```

---

## Bước 4: Cập nhật `/analyze` để trả về problem type

Thêm `problem_type` vào `ProblemSchema`:

```python
class ProblemSchema(BaseModel):
    title: str
    description: str
    input_format: str
    output_format: str
    constraints: str
    sample_input: str
    sample_output: str
    problem_type: str = ""         # NEW
    secondary_type: str = ""       # NEW
    tle_strategy: str = ""         # NEW – filled by complexity analyzer
```

Trong `problem_analyzer.py`, sau khi parse, gọi classifier và điền vào schema.

---

## Bước 5: Cập nhật Java model

File: `src/main/java/org/example/model/Problem.java`

```java
private String problemType = "";
private String secondaryType = "";
private String tleStrategy = "";

// getters/setters
```

---

## Bước 6: Hiển thị problem type trong UI

Tab "Phan tich" – thêm dòng hiển thị:

```
Problem Type: TREE (confidence: 90%)
TLE Strategy: Bamboo tree of n=max
```

Trong `ProblemInputController.java`, thêm Label bind với `problem.getProblemType()`.

---

## Verification

```powershell
# 1. Test classifier
$body = '{"problem":{"title":"Shortest Path","description":"Given a graph find shortest path","constraints":"n<=100000 m<=200000","..."}}'
Invoke-RestMethod -Method Post -Uri http://localhost:8000/analyze `
    -ContentType "application/json" -Body $body
# Kiểm tra response.problem_type == "GRAPH_UNDIRECTED" hoặc "GRAPH_DIRECTED"

# 2. Test với bài tree DP
# Kiểm tra sinh test case có "bamboo" hoặc "star tree" trong description

# 3. Compile Java
mvn -q compile
```

## Definition of Done

- [ ] Classifier nhận ra đúng problem type cho 8/10 bài test thủ công.
- [ ] Edge case hints xuất hiện trong description của SMALL test cases.
- [ ] Killer test cases của TREE problem có bamboo/star graph.
- [ ] Problem type hiển thị trong tab Phan tich.
- [ ] Không có regression với các bài đã chạy đúng trước đó.
