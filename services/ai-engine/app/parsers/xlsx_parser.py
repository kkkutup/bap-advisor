import os
from typing import Any, Dict, List
import openpyxl
from app.parsers.base import BaseParser, ParsedDocument


class XlsxParser(BaseParser):
    def parse(self, file_path: str, **kwargs) -> ParsedDocument:
        if not os.path.exists(file_path):
            raise FileNotFoundError(f"File not found: {file_path}")

        wb = openpyxl.load_workbook(file_path, data_only=True)
        markdown_sections: List[str] = []
        structured_tables: List[Dict[str, Any]] = []

        for sheet_name in wb.sheetnames:
            sheet = wb[sheet_name]
            raw_rows: List[List[str]] = []

            for row in sheet.iter_rows(values_only=True):
                # Check if entire row is empty
                if not any(cell is not None for cell in row):
                    continue
                formatted_cells = [
                    self._format_cell_value(cell) for cell in row
                ]
                # Trim trailing empty strings
                while formatted_cells and formatted_cells[-1] == "":
                    formatted_cells.pop()
                if formatted_cells:
                    raw_rows.append(formatted_cells)

            if not raw_rows:
                continue

            # Determine max cols
            max_cols = max(len(r) for r in raw_rows)
            padded_rows = [r + [""] * (max_cols - len(r)) for r in raw_rows]

            headers = padded_rows[0]
            data_rows = padded_rows[1:]

            md_section = [f"### Sayfa: {sheet_name}"]
            md_table = self._rows_to_markdown_table(headers, data_rows)
            md_section.append(md_table)
            markdown_sections.append("\n\n".join(md_section))

            structured_tables.append({
                "sheet_name": sheet_name,
                "headers": headers,
                "rows": data_rows,
                "row_count": len(data_rows),
                "col_count": max_cols
            })

        wb.close()
        full_markdown = "\n\n".join(markdown_sections)

        return ParsedDocument(
            text=full_markdown,
            tables=structured_tables,
            page_count=len(wb.sheetnames),
            ocr_confidence=0.99,
            is_scanned=False,
            metadata={
                "sheet_count": len(wb.sheetnames),
                "sheet_names": wb.sheetnames
            }
        )

    def _format_cell_value(self, cell: Any) -> str:
        if cell is None:
            return ""
        if isinstance(cell, float):
            # If round number, format without decimal or with 2 decimals
            if cell.is_integer():
                return f"{int(cell)}"
            return f"{cell:.2f}"
        return str(cell).strip()

    def _rows_to_markdown_table(self, headers: List[str], rows: List[List[str]]) -> str:
        col_count = len(headers)
        if col_count == 0:
            return ""

        header_line = "| " + " | ".join(h or " " for h in headers) + " |"
        sep_line = "| " + " | ".join("---" for _ in range(col_count)) + " |"
        row_lines = []
        for r in rows:
            cells = (r + [""] * col_count)[:col_count]
            row_lines.append("| " + " | ".join(c or " " for c in cells) + " |")

        return "\n".join([header_line, sep_line] + row_lines)
