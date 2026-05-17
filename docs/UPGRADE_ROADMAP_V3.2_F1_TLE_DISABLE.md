# UPGRADE_ROADMAP_V3.2 – F1: Disable TLE Khi Bài Toán Có N Nhỏ

Ngày tạo: 2026-05-17
Phụ thuộc: README_V3.1.md (snapshot hiện tại)

---

## 1. Mục Tiêu

Một số bài toán (xử lý chuỗi cơ bản, công thức toán học kín, bài có `N ≤ 100`) không có
ý nghĩa khi sinh code TLE vì bottleneck không phải là hiệu năng. Với những bài này:

- Nút **TLE** trong tab Codegen phải bị **disable** (làm mờ, không click được).
- Hiển thị **Tooltip** giải thích lý do.
- Ngưỡng quyết định: `N_max < 1000` (configurable bằng constant).

---

## 2. Định Nghĩa "N Nhỏ"

| Điều kiện | Kết quả |
|---|---|
| Constraint lớn nhất được parse ra `< N_SMALL_THRESHOLD` (mặc định `1000`) | Disable TLE |
| Không parse được constraint nào (bài toán mù) | Giữ nguyên (TLE enabled) |
| `problem_type` thuộc nhóm `MATH_FORMULA` hoặc `STRING_BASIC` | Disable TLE (bổ sung) |

`N_SMALL_THRESHOLD = 1000` đặt là constant trong `ResultController.java`.

---

## 3. Thay Đổi Python – `ai_service`

### 3.1 `ai_service/models/schemas.py`

Thêm field vào `ProblemSchema` (nếu chưa có):

```python
max_constraint_n: Optional[int] = None
is_small_n: Optional[bool] = False
```

- `max_constraint_n`: giá trị `N` lớn nhất parse được từ constraints.
- `is_small_n`: `True` nếu `max_constraint_n < 1000` hoặc `problem_type` thuộc nhóm không cần TLE.

### 3.2 `ai_service/services/problem_classifier.py`

Thêm hàm `extract_max_n(constraints_text: str) -> Optional[int]`:

```python
import re

def extract_max_n(constraints_text: str) -> Optional[int]:
    """
    Parse constraint text, tìm giá trị N lớn nhất.
    Ví dụ: "1 ≤ N ≤ 100 000" -> 100000
    Ví dụ: "N <= 2000"        -> 2000
    """
    # Xóa dấu cách trong số (100 000 -> 100000)
    cleaned = re.sub(r'(\d)\s(\d)', r'\1\2', constraints_text)
    # Tìm các pattern "N <= X" hoặc "N ≤ X"
    patterns = [
        r'[Nn]\s*[<≤]=?\s*([0-9]{1,9})',
        r'([0-9]{1,9})\s*[<≤]=?\s*[Nn]\b',  # dạng đảo
    ]
    candidates = []
    for p in patterns:
        for m in re.finditer(p, cleaned):
            try:
                candidates.append(int(m.group(1)))
            except (IndexError, ValueError):
                pass
    return max(candidates) if candidates else None
```

Sau khi classify problem, gán thêm:

```python
max_n = extract_max_n(problem.constraints or "")
problem.max_constraint_n = max_n

SMALL_N_PROBLEM_TYPES = {"MATH_FORMULA", "STRING_BASIC", "AD_HOC_SIMPLE"}
problem.is_small_n = (
    (max_n is not None and max_n < 1000)
    or problem.problem_type in SMALL_N_PROBLEM_TYPES
)
```

Gọi đoạn này ở cuối `classify_problem(...)` trước khi return.

### 3.3 `ai_service/services/pipeline_orchestrator.py`

Không cần sửa thêm nếu `ProblemSchema` đã có field mới và classifier đã gán.
Verify rằng field `is_small_n` và `max_constraint_n` được serialize trong SSE event `DONE`.

---

## 4. Thay Đổi Java

### 4.1 `src/main/java/org/example/model/Problem.java`

Thêm 2 field:

```java
@SerializedName("max_constraint_n")
private Integer maxConstraintN;

@SerializedName("is_small_n")
private Boolean isSmallN;

// getters
public Integer getMaxConstraintN() { return maxConstraintN; }
public boolean isSmallN() { return Boolean.TRUE.equals(isSmallN); }
```

### 4.2 `src/main/java/org/example/ui/controller/ResultController.java`

Thêm constant ở đầu class:

```java
private static final int N_SMALL_THRESHOLD = 1000;
```

Thêm hàm kiểm tra:

```java
/**
 * Trả về true nếu bài toán có N nhỏ hoặc không cần kiểm thử hiệu năng.
 * Dùng cả flag từ Python lẫn fallback parse Java-side.
 */
private boolean isTleIrrelevant(Problem problem) {
    if (problem == null) return false;
    // Tin tưởng flag từ Python trước
    if (problem.isSmallN()) return true;
    // Fallback: Java tự kiểm tra
    Integer maxN = problem.getMaxConstraintN();
    if (maxN != null && maxN < N_SMALL_THRESHOLD) return true;
    return false;
}
```

Trong hàm khởi tạo/reset UI (sau khi `problem` được set, thường là trong
`setProblem(Problem p)` hoặc `updateUI()`), thêm logic disable nút TLE:

```java
private void updateTleButtonState() {
    boolean disable = isTleIrrelevant(currentProblem);
    // tleButton là tên fx:id của nút TLE trong FXML
    tleButton.setDisable(disable);
    if (disable) {
        Tooltip tooltip = new Tooltip(
            "Bài toán có giới hạn dữ liệu nhỏ, không yêu cầu kiểm thử hiệu năng (TLE)."
        );
        tooltip.setShowDelay(javafx.util.Duration.millis(300));
        Tooltip.install(tleButton, tooltip);
    } else {
        Tooltip.uninstall(tleButton, tleButton.getTooltip());
    }
}
```

Gọi `updateTleButtonState()` mỗi khi `currentProblem` thay đổi.

Nếu người dùng **vẫn cố trigger TLE** (ví dụ qua keyboard shortcut), guard thêm ở
đầu `generateCodeWithValidation(...)`:

```java
if ("TLE".equals(codeType) && isTleIrrelevant(currentProblem)) {
    showInfo("TLE không khả dụng", 
        "Bài toán có giới hạn dữ liệu nhỏ, không yêu cầu kiểm thử hiệu năng.");
    return;
}
```

### 4.3 FXML (nếu cần)

Nếu nút TLE trong FXML chưa có `fx:id`, thêm:

```xml
<Button fx:id="tleButton" text="TLE" ... />
```

Tooltip FXML tĩnh **không** cần thêm vì logic xử lý động từ Java.

---

## 5. Luồng Hoàn Chỉnh Sau Upgrade

```
Pipeline DONE event
  -> Java parse Problem (có is_small_n, max_constraint_n)
  -> ResultController.setProblem(problem)
  -> updateTleButtonState()
     -> is_small_n=true  -> tleButton.setDisable(true) + Tooltip
     -> is_small_n=false -> tleButton.setDisable(false)
```

---

## 6. Verify

```powershell
# Python compile check
.\ai_service\.venv\Scripts\python.exe -m compileall -q -x "ai_service[\\/][.]venv" ai_service

# Java compile check
& 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.1\plugins\maven\lib\maven3\bin\mvn.cmd' -q compile
```

Test case thủ công:

| Bài toán | N_max | Kết quả mong đợi |
|---|---|---|
| A+B Problem (N/A) | null | TLE enabled |
| Bài tính diện tích (N ≤ 100) | 100 | TLE disabled + Tooltip |
| Bài graph (N ≤ 100 000) | 100000 | TLE enabled |
| problem_type = MATH_FORMULA | bất kỳ | TLE disabled |

---

## 7. Lưu Ý

- `N_SMALL_THRESHOLD` chỉ cần thay ở một chỗ (`ResultController.java`). Không hardcode rải rác.
- Python-side flag `is_small_n` là nguồn chính xác nhất; Java-side là fallback an toàn.
- Không xóa code TLE hiện tại, chỉ disable UI. Logic vẫn còn nguyên cho tương lai.
- Nếu `problem` là `null` (chưa analyze xong), nút TLE giữ trạng thái mặc định (enabled).
