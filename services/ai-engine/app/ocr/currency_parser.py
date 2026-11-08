import re
from typing import Optional


class CurrencyParser:
    """
    Robust parser for Turkish currency formats and durations.
    """

    @classmethod
    def parse_tl_amount(cls, raw_val: Optional[str]) -> Optional[float]:
        """
        Parses Turkish currency strings:
        - "125.000,00 TL" -> 125000.00
        - "125.000 TL"    -> 125000.00
        - "₺80.000,50"    -> 80000.50
        - "300000"        -> 300000.00
        - "1.250,00"      -> 1250.00
        """
        if not raw_val:
            return None

        clean_str = str(raw_val).strip()
        # Remove currency symbols and labels
        clean_str = re.sub(r"[₺TLTRYtl\s]", "", clean_str)
        # Remove trailing slash or rate qualifiers like "/gün"
        clean_str = re.sub(r"/(?:gün|gun|adet|ay|kişi).*", "", clean_str)

        # Match numbers with dots and commas:
        # e.g. "125.000,00", "125000,00", "125.000", "125000.50", "300000"
        match = re.search(r"[-+]?[0-9]+(?:[\.,][0-9]+)*", clean_str)
        if not match:
            return None

        num_str = match.group(0)

        # Turkish convention: dot is thousand separator, comma is decimal separator
        # e.g. "125.000,50" -> dot removed, comma replaced with dot
        if "." in num_str and "," in num_str:
            num_str = num_str.replace(".", "").replace(",", ".")
        elif "," in num_str:
            # Check if comma is decimal (1 or 2 digits after comma)
            parts = num_str.split(",")
            if len(parts) == 2 and len(parts[1]) in (1, 2):
                num_str = parts[0] + "." + parts[1]
            elif len(parts) == 2 and len(parts[1]) == 3:
                # e.g. "125,000" as thousand separator
                num_str = parts[0] + parts[1]
            else:
                num_str = num_str.replace(",", ".")
        elif "." in num_str:
            # Could be thousand separator "125.000" or decimal "125.50"
            parts = num_str.split(".")
            if len(parts) == 2 and len(parts[1]) == 3 and len(parts[0]) <= 3:
                # "125.000" -> thousand separator!
                num_str = parts[0] + parts[1]
            elif len(parts) > 2:
                # Multiple dots: "1.250.000"
                num_str = "".join(parts)

        try:
            val = float(num_str)
            return round(val, 2)
        except ValueError:
            return None

    @classmethod
    def parse_duration_months(cls, raw_val: Optional[str]) -> Optional[int]:
        """
        Parses project duration strings:
        - "18 Ay" -> 18
        - "24 ay" -> 24
        - "3 Yıl" -> 36
        - "1.5 Yıl" -> 18
        - "2 yıl" -> 24
        """
        if not raw_val:
            return None

        val_str = str(raw_val).strip().lower()

        # Check for year patterns: "3 yıl", "2 yil", "1.5 yıl"
        year_match = re.search(r"([0-9]+(?:[\.,][0-9]+)?)\s*(?:yıl|yil|year)", val_str)
        if year_match:
            try:
                y_val = float(year_match.group(1).replace(",", "."))
                return int(round(y_val * 12))
            except ValueError:
                pass

        # Check for month patterns: "24 ay", "18 ay", "12"
        month_match = re.search(r"([0-9]{1,3})\s*(?:ay|month)?", val_str)
        if month_match:
            try:
                return int(month_match.group(1))
            except ValueError:
                pass

        return None
