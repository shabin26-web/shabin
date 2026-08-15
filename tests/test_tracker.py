from datetime import date
from decimal import Decimal

import pytest

from expenses.models import Expense
from expenses.tracker import Tracker


@pytest.fixture
def tracker():
    return Tracker(
        [
            Expense.of("Office chair", "450.00", "furniture", "2026-01-14"),
            Expense.of("Domain renewal", "62.50", "software", "2026-01-20"),
            Expense.of("Team lunch", "180.75", "meals", "2026-02-03"),
            Expense.of("Cloud hosting", "240.00", "software", "2026-02-11"),
        ]
    )


def test_empty_tracker_totals_zero():
    assert Tracker().total() == Decimal("0")
    assert Tracker().largest() is None


def test_add_grows_the_tracker():
    tracker = Tracker()
    tracker.add(Expense.of("Paper", "35.20", "supplies", "2026-02-27"))

    assert len(tracker) == 1
    assert tracker.total() == Decimal("35.20")


def test_total(tracker):
    assert tracker.total() == Decimal("933.25")


def test_total_by_category_sums_and_sorts(tracker):
    totals = tracker.total_by_category()

    assert totals == {
        "furniture": Decimal("450.00"),
        "software": Decimal("302.50"),
        "meals": Decimal("180.75"),
    }
    assert list(totals) == ["furniture", "software", "meals"]


def test_between_is_inclusive_on_both_ends(tracker):
    january = tracker.between(date(2026, 1, 14), date(2026, 1, 20))

    assert len(january) == 2
    assert january.total() == Decimal("512.50")


def test_between_does_not_mutate_the_original(tracker):
    tracker.between(date(2026, 1, 1), date(2026, 1, 31))

    assert len(tracker) == 4


def test_largest(tracker):
    assert tracker.largest().description == "Office chair"
