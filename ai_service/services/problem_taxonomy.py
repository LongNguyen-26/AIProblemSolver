PROBLEM_TYPES = {
    "ARRAY_SEQUENCE": {
        "keywords": ["array", "sequence", "subarray", "prefix sum", "sliding window", "permutation"],
        "edge_cases": [
            "n=1 (single element)",
            "all elements equal",
            "already sorted and reverse sorted",
            "all zeros or all maximum values",
            "n=max_n stress case",
        ],
        "killer_strategy": "n=max with adversarial order, duplicates, and overflow-prone values",
        "input_generator_hint": "Generate an array of n integers within constraint range.",
    },
    "GRAPH_UNDIRECTED": {
        "keywords": ["undirected", "graph", "vertices", "edges", "connected", "path", "cycle"],
        "edge_cases": [
            "n=1 with no edges",
            "disconnected graph",
            "tree with n-1 edges",
            "cycle graph",
            "dense or complete graph when allowed",
        ],
        "killer_strategy": "long path or star graph to stress DFS/BFS depth and O(nm) logic",
        "input_generator_hint": "Generate n, m and undirected edge pairs with valid node ids.",
    },
    "GRAPH_DIRECTED": {
        "keywords": ["directed", "dag", "topological", "scc", "tournament", "strongly connected"],
        "edge_cases": [
            "DAG with one source",
            "directed cycle",
            "single-node self-loop if allowed",
            "long directed chain",
            "multiple SCC components",
        ],
        "killer_strategy": "long directed chain or dense SCC-heavy graph",
        "input_generator_hint": "Generate directed edge pairs; include DAG and cyclic variants.",
    },
    "TREE": {
        "keywords": ["tree", "rooted tree", "parent", "children", "lca", "subtree"],
        "edge_cases": [
            "n=1",
            "linear chain (bamboo)",
            "star tree",
            "balanced binary tree",
            "n=max bamboo stress recursion depth",
        ],
        "killer_strategy": "bamboo tree of n=max or star tree with adversarial queries",
        "input_generator_hint": "Generate n-1 edges forming a connected acyclic tree.",
    },
    "DP_1D": {
        "keywords": ["dp", "dynamic programming", "maximize", "minimize", "count ways", "knapsack"],
        "edge_cases": [
            "n=1",
            "n=max",
            "all items identical",
            "capacity=0 or capacity=max",
            "impossible answer is 0",
        ],
        "killer_strategy": "n=max with adversarial item sizes or transitions",
        "input_generator_hint": "Generate item/value/state arrays matching the DP constraints.",
    },
    "DP_2D": {
        "keywords": ["grid", "matrix", "2d dp", "paths in grid", "lcs", "edit distance"],
        "edge_cases": [
            "1x1 grid",
            "1xN or Nx1 grid",
            "fully blocked grid if obstacles exist",
            "n=m=max grid",
            "uniform and checkerboard grids",
        ],
        "killer_strategy": "n=m=max grid with adversarial obstacle/value pattern",
        "input_generator_hint": "Generate dimensions and a full grid of chars or integers.",
    },
    "DP_BITMASK": {
        "keywords": ["bitmask", "subset", "tsp", "assignment", "permutation dp"],
        "edge_cases": [
            "n=1",
            "n=max for bitmask constraints",
            "all costs equal",
            "impossible assignment if applicable",
        ],
        "killer_strategy": "n at max bitmask limit with random or equal costs",
        "input_generator_hint": "Generate n and matrices/masks for subset-state transitions.",
    },
    "MATH_NUMBER_THEORY": {
        "keywords": ["prime", "gcd", "lcm", "modulo", "factorial", "divisors", "euler", "sieve"],
        "edge_cases": [
            "n=1",
            "large prime",
            "large composite with many divisors",
            "values near max for overflow",
        ],
        "killer_strategy": "large prime or max value that breaks slow factorization",
        "input_generator_hint": "Generate integers within constraints, including primes and composites.",
    },
    "MATH_COMBINATORICS": {
        "keywords": ["combination", "combinatorics", "permutation", "binomial", "choose", "ways"],
        "edge_cases": [
            "n=0 or k=0",
            "n=k",
            "k=1",
            "n=max, k=n/2",
            "cases needing modular arithmetic",
        ],
        "killer_strategy": "n=max and k near n/2",
        "input_generator_hint": "Generate n, k and modulo-related values when present.",
    },
    "STRING": {
        "keywords": ["string", "substring", "palindrome", "prefix", "suffix", "pattern", "kmp", "hash"],
        "edge_cases": [
            "length=1",
            "all same characters",
            "alternating characters",
            "pattern equals text",
            "length=max",
        ],
        "killer_strategy": "length=max all-same or highly periodic string",
        "input_generator_hint": "Generate strings over the allowed alphabet with edge patterns.",
    },
    "GEOMETRY": {
        "keywords": ["point", "points", "polygon", "convex hull", "distance", "area", "intersection"],
        "edge_cases": [
            "all points collinear",
            "n=1 or n=2",
            "coincident points",
            "all points on convex hull",
            "large coordinate extremes",
        ],
        "killer_strategy": "n=max points on a circle or collinear line",
        "input_generator_hint": "Generate coordinate pairs within bounds, including degenerate cases.",
    },
    "GREEDY": {
        "keywords": ["greedy", "minimum cost", "maximum profit", "schedule", "interval", "deadline"],
        "edge_cases": [
            "n=1",
            "all intervals same",
            "all intervals overlap",
            "no overlap",
            "tie-breaking-heavy input",
        ],
        "killer_strategy": "n=max with adversarial intervals/tasks and equal keys",
        "input_generator_hint": "Generate intervals, tasks, or sorted choices with ties.",
    },
    "BINARY_SEARCH": {
        "keywords": ["binary search", "sorted", "minimum possible", "maximum possible", "feasible", "monotone"],
        "edge_cases": [
            "answer at lower boundary",
            "answer at upper boundary",
            "all values equal",
            "n=1",
            "n=max",
        ],
        "killer_strategy": "n=max with answer at extreme boundary",
        "input_generator_hint": "Generate monotone feasibility data or sorted arrays.",
    },
    "DATA_STRUCTURE": {
        "keywords": ["segment tree", "fenwick", "bit", "range query", "update", "heap", "priority queue"],
        "edge_cases": [
            "n=1",
            "all queries on same index",
            "alternating update/query",
            "full-range queries",
            "n=max and q=max",
        ],
        "killer_strategy": "n=q=max with alternating updates and full-range queries",
        "input_generator_hint": "Generate initial data and q operations following operation syntax.",
    },
    "CONSTRUCTIVE": {
        "keywords": ["construct", "build", "find any", "output any valid", "any solution", "print any"],
        "edge_cases": [
            "n=1 trivial construction",
            "minimal impossible case",
            "minimal non-trivial case",
            "n=max",
            "tight constraints",
        ],
        "killer_strategy": "n=max with tight constructive constraints and impossible cases",
        "input_generator_hint": "Generate parameters that include both possible and impossible constructions.",
    },
    "GAME_THEORY": {
        "keywords": ["game", "grundy", "nim", "optimal play", "first player", "second player", "winning"],
        "edge_cases": [
            "trivially winning position",
            "trivially losing position",
            "n=1",
            "symmetric position",
            "n=max",
        ],
        "killer_strategy": "n=max with non-obvious winning/losing state",
        "input_generator_hint": "Generate valid game states and move parameters.",
    },
}


DEFAULT_PROBLEM_TYPE = "ARRAY_SEQUENCE"


def type_strategy(problem_type: str) -> dict:
    return PROBLEM_TYPES.get(problem_type or "", PROBLEM_TYPES[DEFAULT_PROBLEM_TYPE])
