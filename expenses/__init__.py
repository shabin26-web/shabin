"""A tiny expense-tracking library used as practice material."""

from expenses.models import Expense
from expenses.tracker import Tracker

__all__ = ["Expense", "Tracker"]
__version__ = "0.1.0"
