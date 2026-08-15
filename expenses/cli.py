"""A minimal command line front end, so the library does something visible.

    python3 -m expenses.cli demo
"""

import sys
from decimal import Decimal

from expenses.models import Expense
from expenses.tracker import Tracker

SAMPLE = [
    ("Office chair", "450.00", "furniture", "2026-01-14"),
    ("Domain renewal", "62.50", "software", "2026-01-20"),
    ("Team lunch", "180.75", "meals", "2026-02-03"),
    ("Cloud hosting", "240.00", "software", "2026-02-11"),
    ("Printer paper", "35.20", "supplies", "2026-02-27"),
]


def money(amount: Decimal) -> str:
    """Format an amount with two decimals and thousands separators."""
    return f"{amount:,.2f}"


def demo() -> int:
    tracker = Tracker(Expense.of(*row) for row in SAMPLE)

    print(f"{len(tracker)} expenses recorded\n")
    print("By category")
    for category, subtotal in tracker.total_by_category().items():
        print(f"  {category:<12} {money(subtotal):>10}")

    biggest = tracker.largest()
    print(f"\nLargest      {biggest.description} ({money(biggest.amount)})")
    print(f"Total        {money(tracker.total())}")
    return 0


def main(argv: list[str]) -> int:
    if len(argv) == 2 and argv[1] == "demo":
        return demo()
    print(__doc__, file=sys.stderr)
    return 1


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
