import pytest
from app.ocr.currency_parser import CurrencyParser


def test_turkish_currency_formats():
    assert CurrencyParser.parse_tl_amount("125.000,00 TL") == 125000.00
    assert CurrencyParser.parse_tl_amount("125.000 TL") == 125000.00
    assert CurrencyParser.parse_tl_amount("₺80.000,50") == 80000.50
    assert CurrencyParser.parse_tl_amount("300000") == 300000.00
    assert CurrencyParser.parse_tl_amount("118.500,00") == 118500.00
    assert CurrencyParser.parse_tl_amount("1.250 TL/gün") == 1250.00
    assert CurrencyParser.parse_tl_amount("2.800,00 TL/gün") == 2800.00
    assert CurrencyParser.parse_tl_amount("120000 TL (KDV Dahil)") == 120000.00
    assert CurrencyParser.parse_tl_amount("0,00 TL") == 0.00
    assert CurrencyParser.parse_tl_amount("") is None
    assert CurrencyParser.parse_tl_amount(None) is None


def test_duration_formats():
    assert CurrencyParser.parse_duration_months("18 Ay") == 18
    assert CurrencyParser.parse_duration_months("24 ay") == 24
    assert CurrencyParser.parse_duration_months("3 Yıl") == 36
    assert CurrencyParser.parse_duration_months("1.5 yıl") == 18
    assert CurrencyParser.parse_duration_months("2 yıl") == 24
    assert CurrencyParser.parse_duration_months("42 Ay") == 42
    assert CurrencyParser.parse_duration_months("12") == 12
    assert CurrencyParser.parse_duration_months("") is None
    assert CurrencyParser.parse_duration_months(None) is None
