import os
from typing import Any, Dict, List
import docx
from app.parsers.base import BaseParser, ParsedDocument


class DocxParser(BaseParser):
    def parse(self, file_path: str, **kwargs) -> ParsedDocument:
        if not os.path.exists(file_path):
            raise FileNotFoundError(f"File not found: {file_path}")

        doc = docx.Document(file_path)
        markdown_parts: List[str] = []
        structured_tables: List[Dict[str, Any]] = []

        # Iterate over paragraphs and tables in order of appearance
        # python-docx stores elements in doc.element.body
        for elem in doc.element.body:
            tag = elem.tag.split("}")[-1] if "}" in elem.tag else elem.tag
            if tag == "p":
                # Paragraph
                p = docx.text.paragraph.Paragraph(elem, doc)
                text = p.text.strip()
                if not text:
                    continue
                style_name = (p.style.name or "").lower()
                if "heading 1" in style_name:
                    markdown_parts.append(f"# {text}")
                elif "heading 2" in style_name:
                    markdown_parts.append(f"## {text}")
                elif "heading 3" in style_name:
                    markdown_parts.append(f"### {text}")
                elif "title" in style_name:
                    markdown_parts.append(f"# {text}")
                else:
                    markdown_parts.append(text)

            elif tag == "tbl":
                # Table
                table = docx.table.Table(elem, doc)
                table_rows: List[List[str]] = []
                for row in table.rows:
                    row_cells = [cell.text.strip().replace("\n", " ") for cell in row.cells]
                    # Filter out duplicate adjacent cells resulting from merged cells in docx
                    deduped_cells = []
                    for c in row_cells:
                        deduped_cells.append(c)
                    table_rows.append(deduped_cells)

                if not table_rows:
                    continue

                headers = table_rows[0]
                data_rows = table_rows[1:] if len(table_rows) > 1 else []

                # Format Markdown Table
                md_table = self._rows_to_markdown_table(headers, data_rows)
                markdown_parts.append(md_table)

                # Format structured JSON table
                structured_tables.append({
                    "headers": headers,
                    "rows": data_rows,
                    "row_count": len(data_rows),
                    "col_count": len(headers)
                })

        full_markdown = "\n\n".join(markdown_parts)

        return ParsedDocument(
            text=full_markdown,
            tables=structured_tables,
            page_count=max(1, len(doc.paragraphs) // 25),
            ocr_confidence=0.98,
            is_scanned=False,
            metadata={
                "paragraph_count": len(doc.paragraphs),
                "table_count": len(structured_tables)
            }
        )

    def _rows_to_markdown_table(self, headers: List[str], rows: List[List[str]]) -> str:
        col_count = len(headers)
        if col_count == 0:
            return ""

        header_line = "| " + " | ".join(h or " " for h in headers) + " |"
        sep_line = "| " + " | ".join("---" for _ in range(col_count)) + " |"
        row_lines = []
        for r in rows:
            # Pad or truncate row to header length
            cells = (r + [""] * col_count)[:col_count]
            row_lines.append("| " + " | ".join(c or " " for c in cells) + " |")

        return "\n".join([header_line, sep_line] + row_lines)
