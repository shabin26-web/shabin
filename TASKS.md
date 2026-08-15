# Task sheet

Work down the list in order — each level assumes the ones before it. Tick boxes
as you go (edit this file, commit the change; that's practice too).

Commands for every step are in [CONTRIBUTING.md](CONTRIBUTING.md).

**How to mark a task done:** change `- [ ]` to `- [x]`.

---

## Level 1 — Get it running

Goal: prove your machine and the repo work before changing anything.

- [ ] Clone the repository to your own machine
- [ ] Install the dev dependencies (`python3 -m pip install -r requirements-dev.txt`)
- [ ] Run the tests and see **15 passed**
- [ ] Run `python3 -m expenses.cli demo` and see the expense summary
- [ ] Run `git log --oneline` and read the history
- [ ] Run `git status` on a clean checkout — note that it says "nothing to commit"

**Done when:** tests pass and the demo prints a total of 968.45.

---

## Level 2 — Your first pull request

Goal: one full trip around the loop, with a change too small to go wrong.

- [ ] Create a branch called `add-yourself-to-readme`
- [ ] Add a `## Who's practising here` section at the bottom of `README.md` with your name
- [ ] Commit it with a clear message
- [ ] Push the branch to GitHub
- [ ] Open a pull request and fill in the template properly
- [ ] Watch the CI checks run — wait for the green tick
- [ ] Merge the PR, then delete the branch on GitHub
- [ ] Back on your machine: `git checkout main && git pull origin main` and confirm your change is there

**Done when:** your name is on `main` and the branch is gone.

---

## Level 3 — Change real code

Goal: change behaviour, and prove it with a test.

- [ ] Open an issue using the **Feature request** template describing the change below
- [ ] Branch off the latest `main`
- [ ] Add a method `Tracker.count_by_category()` returning `{category: number_of_expenses}`
- [ ] Add tests for it in `tests/test_tracker.py`, including the empty-tracker case
- [ ] Run tests and lint locally — both green
- [ ] Push and open a PR whose description says `Closes #<your issue number>`
- [ ] Merge it, and confirm the issue closed **automatically**

**Done when:** the issue closed by itself when the PR merged. That link between
PR and issue is the point of this level.

---

## Level 4 — Watch CI catch a mistake

Goal: see the safety net work. You are going to break it on purpose.

- [ ] Branch: `break-the-build`
- [ ] In `expenses/tracker.py`, change `total()` to start from `Decimal("1")` instead of `Decimal("0")`
- [ ] Run the tests locally and read the failure — which test caught it, and what did it expect?
- [ ] Commit and push anyway, and open a PR
- [ ] On the PR, find the red X → **Details** → read the failing job's log
- [ ] Fix it in a second commit on the same branch, push, and watch the same PR turn green
- [ ] Merge (or close the PR without merging — decide which is right here and why)

**Done when:** you can point at the exact line in the CI log that told you what
was wrong.

---

## Level 5 — Review and conflicts

Goal: the two things that only show up when more than one person is involved.

- [ ] Create a branch `add-average` and add `Tracker.average()` (total ÷ count, and `None` when empty) — with tests
- [ ] Open the PR, then **review your own PR**: go to Files changed and leave a comment on a specific line
- [ ] Push a commit that responds to your own comment, then resolve the thread
- [ ] Now make a conflict on purpose:
  - [ ] On `main`, edit the first line of `README.md` and commit directly (yes, directly — this once)
  - [ ] On a second branch from the *old* `main`, edit that same first line differently, push, open a PR
  - [ ] GitHub says the branch has conflicts. Resolve it by merging `main` into your branch locally
  - [ ] Push the resolution and merge
- [ ] Read the final `git log --graph --oneline --all` and trace what happened

**Done when:** you have resolved a conflict yourself and can explain what the
`<<<<<<<` / `=======` / `>>>>>>>` markers meant.

---

## Level 6 — Make it yours

Goal: no instructions this time. Pick anything.

- [ ] Decide on a feature (ideas below), open an issue for it, and ship it as a PR
- [ ] Add a test that would fail without your change

Ideas:

| Idea | Where |
| --- | --- |
| `Tracker.monthly_totals()` → `{"2026-01": Decimal("512.50"), ...}` | `tracker.py` |
| A `--category` filter for the CLI | `cli.py` |
| Load expenses from a CSV file | new `expenses/csv_loader.py` |
| Reject expenses dated in the future | `models.py` |
| Add Python 3.13 to the CI matrix | `.github/workflows/ci.yml` |

**Done when:** you opened the issue, wrote the code and tests, got CI green, and
merged — without following a checklist.

---

## Glossary

| Term | What it means |
| --- | --- |
| **repository (repo)** | The project and its full history |
| **clone** | Your local copy of the repo |
| **branch** | A parallel line of work, so `main` stays working |
| **commit** | One saved change, with a message explaining it |
| **push / pull** | Send your commits to GitHub / bring GitHub's down |
| **pull request (PR)** | A proposal to merge your branch into `main`, where review happens |
| **CI** | Automated checks that run on every PR (here: tests + lint) |
| **merge conflict** | Two branches changed the same lines; Git needs you to choose |
| **main** | The branch that should always work |
