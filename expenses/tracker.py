"""Collect expenses and answer simple questions about them."""

from collections import defaultdict
from collections.abc import Iterable, Iterator
from datetime import date
from decimal import Decimal

from expenses.models import Expense


class Tracker:
    """An in-memory collection of expenses."""

    def __init__(self, expenses: Iterable[Expense] | None = None) -> None:
        self._expenses: list[Expense] = list(expenses or [])

    def __len__(self) -> int:
        return len(self._expenses)

    def __iter__(self) -> Iterator[Expense]:
        return iter(self._expenses)

    def add(self, expense: Expense) -> None:
        """Record one expense."""
        self._expenses.append(expense)

    def total(self) -> Decimal:
        """Sum of every recorded amount."""
        return sum((e.amount for e in self._expenses), start=Decimal("0"))

    def total_by_category(self) -> dict[str, Decimal]:
        """Sum per category, largest first."""
        sums: dict[str, Decimal] = defaultdict(lambda: Decimal("0"))
        for expense in self._expenses:
            sums[expense.category] += expense.amount
        return dict(sorted(sums.items(), key=lambda item: item[1], reverse=True))

    def between(self, start: date, end: date) -> "Tracker":
        """A new Tracker holding only expenses in [start, end] — both inclusive."""
        return Tracker(e for e in self._expenses if start <= e.spent_on <= end)

    def largest(self) -> Expense | None:
        """The single biggest expense, or None when nothing is recorded."""
        return max(self._expenses, key=lambda e: e.amount, default=None)
