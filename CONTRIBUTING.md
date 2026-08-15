# The workflow, step by step

This is the reference you'll come back to while working through `TASKS.md`.
Every change follows the same seven steps.

## One-time setup

```bash
git clone https://github.com/shabin26-web/shabin.git
cd shabin
python3 -m pip install -r requirements-dev.txt
python3 -m pytest -q          # should say "15 passed"
```

## The seven steps

### 1. Get up to date

Always start from the latest `main`. Skipping this is the single most common
cause of merge conflicts.

```bash
git checkout main
git pull origin main
```

### 2. Branch

One branch per change. Name it after what it does.

```bash
git checkout -b add-monthly-totals
```

Good: `add-monthly-totals`, `fix-empty-tracker-crash`
Bad: `test`, `branch2`, `shabin-new`

### 3. Change something

Edit the code. Add or update a test for what you changed — a change without a
test is a change nobody can protect.

### 4. Check your work before pushing

```bash
python3 -m pytest -q
python3 -m ruff check .
```

Both must pass. If they fail here, they will fail in CI too — fixing it now is
faster than fixing it after review.

### 5. Commit

```bash
git add .
git status                    # read this. Are you committing what you think?
git commit -m "Add Tracker.monthly_totals()"
```

Write the message in the imperative — "Add X", "Fix Y" — as if completing the
sentence *"This commit will…"*.

### 6. Push

```bash
git push -u origin add-monthly-totals
```

The `-u` sets the upstream, so later pushes on this branch are just `git push`.

### 7. Open a pull request

On GitHub, a banner offers **Compare & pull request**. The PR template fills in
automatically — replace the comments with real answers.

Then:

- CI runs. Wait for the green check.
- If it goes red, click **Details** to read the log, fix it, commit, push again.
  The PR updates itself — you never open a second PR for the same branch.
- Ask for a review.
- Merge when it's green and approved. Delete the branch when GitHub offers.

## Useful commands

| Command | What it does |
| --- | --- |
| `git status` | What's changed, what's staged, what branch you're on |
| `git diff` | The exact lines you changed but haven't staged |
| `git diff --staged` | The lines you *have* staged |
| `git log --oneline -10` | The last ten commits |
| `git switch -` | Jump back to the previous branch |
| `git restore <file>` | Throw away uncommitted changes to a file |
| `git commit --amend` | Fix the last commit message (only before pushing) |

## When something goes wrong

**Committed to `main` by mistake, not pushed yet.** Move the commit to a branch:

```bash
git branch my-work          # bookmark the commit
git reset --hard origin/main
git checkout my-work
```

**Pushed and CI is red.** Normal. Read the failing step's log, fix, push again.

**Merge conflict.** Git is telling you two branches changed the same lines and
it won't guess. Update your branch and resolve:

```bash
git checkout main && git pull origin main
git checkout your-branch
git merge main
# open each conflicted file, keep the right version, delete the <<<< ==== >>>> markers
git add . && git commit
git push
```

**Truly stuck.** Nothing here is precious. Delete the folder, clone again, start over.
