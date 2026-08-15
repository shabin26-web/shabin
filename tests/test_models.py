from datetime import date
from decimal import Decimal

import pytest

from expenses.models import Expense


def test_of_parses_strings():
    expense = Expense.of("Team lunch", "180.75", "meals", "2026-02-03")

    assert expense.amount == Decimal("180.75")
    assert expense.spent_on == date(2026, 2, 3)


def test_amount_is_exact_not_float():
    a = Expense.of("a", "0.10", "misc", "2026-01-01")
    b = Expense.of("b", "0.20", "misc", "2026-01-01")

    assert a.amount + b.amount == Decimal("0.30")


@pytest.mark.parametrize(
    "description, amount, category",
    [
        ("", "10", "meals"),
        ("   ", "10", "meals"),
        ("Lunch", "0", "meals"),
        ("Lunch", "-5", "meals"),
        ("Lunch", "10", ""),
    ],
)
def test_rejects_bad_input(description, amount, category):
    with pytest.raises(ValueError):
        Expense.of(description, amount, category, "2026-01-01")


def test_is_immutable():
    expense = Expense.of("Chair", "450", "furniture", "2026-01-14")

    with pytest.raises(Exception):
        expense.amount = Decimal("1")
