# shabin — GitHub Practice Sandbox

A safe place to practice the everyday GitHub workflow: branches, commits, pull
requests, code review, issues, and automated checks (CI).

Nothing here is production code. Break it, fix it, revert it — that is the point.

## What's inside

| Path | What it is |
| --- | --- |
| `expenses/` | A tiny Python expense-tracker library — the thing you'll actually change |
| `tests/` | Unit tests for that library |
| `pettycash/index.html` | Petty cash imprest app (browser): vouchers with 15% VAT split, top-ups, SAR cash count, reconciliation, Excel/CSV export. Open the file in a browser. |
| `.github/workflows/ci.yml` | GitHub Actions: runs the tests on every push and pull request |
| `.github/ISSUE_TEMPLATE/` | Templates that pre-fill new issues |
| `.github/pull_request_template.md` | Template that pre-fills new pull requests |
| `TASKS.md` | **Start here.** The exercise checklist |
| `CONTRIBUTING.md` | The workflow, step by step, with the exact commands |

## Quick start

```bash
git clone https://github.com/shabin26-web/shabin.git
cd shabin
python3 -m pip install -r requirements-dev.txt
python3 -m pytest -q          # all tests should pass
python3 -m expenses.cli demo  # see the library do something
```

If the tests pass, your setup is working. Now open [TASKS.md](TASKS.md) and work
down the list.

## The loop you are practising

```
main ──┬───────────────────────────────────────────► (merged PRs land here)
       │
       └─► your-branch ──► commit ──► push ──► Pull Request ──► CI runs
                                                    │
                                              review + fix
                                                    │
                                                  merge
```

Every task in `TASKS.md` is one trip around that loop.
