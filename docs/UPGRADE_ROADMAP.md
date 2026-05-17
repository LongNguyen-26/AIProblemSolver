# Upgrade Roadmap – AIProblemSolver v2

Tài liệu này tóm tắt 5 task nâng cấp theo thứ tự thực hiện.
Coding agent đọc file này trước, sau đó thực hiện từng task theo thứ tự.

## Tại sao cần nâng cấp?

| # | Vấn đề | Nguyên nhân gốc |
|---|--------|-----------------|
| 1 | Không sinh đủ số lượng test case | Không có top-up loop; AI trả thiếu không bị phát hiện |
| 2 | Stress testing hay fail → test yếu | Oracle code AI sinh ra hay WA/RE; không có feedback loop |
| 3 | TLE generation fail + chạy chậm | Không classify complexity; validate quá nhiều test cases |
| 4 | Chưa bao quát dạng bài CP | Prompt generic; không có problem-type strategy |
| 5 | Luồng tổng thể là zero-shot | Không có agent workflow với verification + retry có thông tin |

## Thứ tự thực hiện

```
TASK_07 → TASK_08 → TASK_09 → TASK_10 → TASK_11
```

| Task | File | Mô tả | Độ ưu tiên |
|------|------|--------|------------|
| 07 | TASK_07_TESTCASE_COUNT_FIX.md | Fix sinh đủ số lượng + top-up loop | 🔴 Critical |
| 08 | TASK_08_STRESS_TESTING_AGENT.md | Nâng stress testing lên agent workflow | 🔴 Critical |
| 09 | TASK_09_TLE_GENERATION_FIX.md | Fix TLE generation fail + tốc độ | 🟡 High |
| 10 | TASK_10_PROBLEM_TYPE_COVERAGE.md | Thêm problem type classifier + chiến lược riêng | 🟡 High |
| 11 | TASK_11_AGENT_ORCHESTRATION.md | Nâng toàn bộ pipeline lên multi-step agent | 🟢 Medium |

## Nguyên tắc chung cho coding agent

- Không xoá logic hiện có; refactor và wrap thêm.
- Mỗi task có section **Verification** – chạy hết các lệnh đó trước khi sang task tiếp theo.
- Khi sửa Python: `python -m compileall ai_service` sau đó restart service.
- Khi sửa Java: `mvn -q compile` và smoke-test với bài mẫu.
- Mọi thay đổi API schema phải cập nhật cả Java model lẫn Python schema.
