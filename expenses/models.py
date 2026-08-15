"""The data we track: a single expense."""

from dataclasses import dataclass
from datetime import date
from decimal import Decimal


@dataclass(frozen=True)
class Expense:
    """One expense line.

    Amounts are Decimal, never float — money and binary floating point do not
    mix (0.1 + 0.2 != 0.3).
    """

    description: str
    amount: Decimal
    category: str
    spent_on: date

    def __post_init__(self) -> None:
        if not self.description.strip():
            raise ValueError("description must not be empty")
        if self.amount <= 0:
            raise ValueError(f"amount must be positive, got {self.amount}")
        if not self.category.strip():
            raise ValueError("category must not be empty")

    @classmethod
    def of(
        cls,
        description: str,
        amount: str | int | Decimal,
        category: str,
        spent_on: str | date,
    ) -> "Expense":
        """Build an Expense from loose input (strings from a CSV, a form, ...)."""
        return cls(
            description=description,
            amount=Decimal(str(amount)),
            category=category,
            spent_on=date.fromisoformat(spent_on) if isinstance(spent_on, str) else spent_on,
        )
