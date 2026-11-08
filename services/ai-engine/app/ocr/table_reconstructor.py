from typing import Any, Dict, List, Optional, Tuple


class TableReconstructor:
    """
    Reconstructs reading order lines and detects tabular structures from
    2D OCR bounding boxes and text lines.
    """

    @classmethod
    def process_ocr_results(
        cls,
        ocr_results: List[Any],
        line_height_ratio: float = 0.65,
        min_table_rows: int = 2
    ) -> Tuple[str, List[Dict[str, Any]]]:
        """
        Takes raw RapidOCR results: [ [bbox, text, score], ... ]
        Returns (reconstructed_markdown, structured_tables_json)
        """
        if not ocr_results:
            return "", []

        # Normalize and filter
        boxes: List[Dict[str, Any]] = []
        for item in ocr_results:
            if not item or len(item) < 3:
                continue
            poly, text, score = item[0], str(item[1]).strip(), float(item[2])
            if not text:
                continue

            # poly is 4 points: [[x1, y1], [x2, y2], [x3, y3], [x4, y4]]
            xs = [p[0] for p in poly]
            ys = [p[1] for p in poly]
            x_min, x_max = min(xs), max(xs)
            y_min, y_max = min(ys), max(ys)
            y_center = (y_min + y_max) / 2.0
            height = max(1.0, y_max - y_min)

            boxes.append({
                "text": text,
                "score": score,
                "x_min": x_min,
                "x_max": x_max,
                "y_min": y_min,
                "y_max": y_max,
                "y_center": y_center,
                "height": height,
                "poly": poly
            })

        if not boxes:
            return "", []

        # Calculate median box height
        heights = sorted(b["height"] for b in boxes)
        median_height = heights[len(heights) // 2]
        line_threshold = max(8.0, median_height * line_height_ratio)

        # Sort boxes primarily by y_center
        boxes.sort(key=lambda b: b["y_center"])

        # Cluster into lines
        lines: List[List[Dict[str, Any]]] = []
        current_line: List[Dict[str, Any]] = []
        current_y = None

        for b in boxes:
            if current_y is None:
                current_line = [b]
                current_y = b["y_center"]
            elif abs(b["y_center"] - current_y) <= line_threshold:
                current_line.append(b)
                # Update current_y as running average
                current_y = sum(x["y_center"] for x in current_line) / len(current_line)
            else:
                current_line.sort(key=lambda x: x["x_min"])
                lines.append(current_line)
                current_line = [b]
                current_y = b["y_center"]

        if current_line:
            current_line.sort(key=lambda x: x["x_min"])
            lines.append(current_line)

        # Now detect tables vs paragraphs
        markdown_blocks: List[str] = []
        structured_tables: List[Dict[str, Any]] = []

        table_keywords = {
            "sıra", "sira", "no", "kalem", "açıklama", "aciklama", "miktar",
            "adet", "birim", "fiyat", "tutar", "toplam", "kategori", "gerekçe"
        }

        i = 0
        while i < len(lines):
            line = lines[i]
            # Check if this line looks like a table row (>= 2 separated cells)
            # or matches table header keywords
            is_table_candidate = len(line) >= 2 or any(
                kw in line[0]["text"].lower() for kw in table_keywords
            )

            if is_table_candidate and (i + 1 < len(lines) and len(lines[i + 1]) >= 2):
                # Collect table lines
                table_lines: List[List[Dict[str, Any]]] = [line]
                j = i + 1
                while j < len(lines):
                    next_line = lines[j]
                    # If line has multiple cells or seems aligned
                    if len(next_line) >= 2 or (len(next_line) == 1 and j < i + 3):
                        table_lines.append(next_line)
                        j += 1
                    else:
                        break

                if len(table_lines) >= min_table_rows:
                    # Construct markdown & structured table
                    md_table, struct_tbl = cls._build_table_from_clustered_lines(table_lines)
                    if md_table:
                        markdown_blocks.append(md_table)
                    if struct_tbl:
                        structured_tables.append(struct_tbl)
                    i = j
                    continue

            # Regular text line
            line_text = "  ".join(b["text"] for b in line)
            # Check if title or heading
            if line[0]["height"] > median_height * 1.35 and len(line_text) < 100:
                markdown_blocks.append(f"## {line_text}")
            else:
                markdown_blocks.append(line_text)
            i += 1

        full_markdown = "\n\n".join(markdown_blocks)
        return full_markdown, structured_tables

    @classmethod
    def _build_table_from_clustered_lines(
        cls, table_lines: List[List[Dict[str, Any]]]
    ) -> Tuple[str, Optional[Dict[str, Any]]]:
        if not table_lines:
            return "", None

        # Build column boundary clusters from all cells
        all_cells = [cell for line in table_lines for cell in line]
        if not all_cells:
            return "", None

        # Use first line or line with max cells as column anchor
        header_line = table_lines[0]
        # If first line has only 1 cell, check second line
        if len(header_line) == 1 and len(table_lines) > 1:
            header_line = table_lines[1]

        # Extract column texts
        raw_rows: List[List[str]] = []
        for line in table_lines:
            row = [c["text"] for c in line]
            raw_rows.append(row)

        if not raw_rows:
            return "", None

        max_cols = max(len(r) for r in raw_rows)
        if max_cols < 2:
            return "\n".join(" ".join(r) for r in raw_rows), None

        # Normalize rows to max_cols
        padded_rows = [r + [""] * (max_cols - len(r)) for r in raw_rows]
        headers = padded_rows[0]
        data_rows = padded_rows[1:]

        # Markdown representation
        header_str = "| " + " | ".join(h or " " for h in headers) + " |"
        sep_str = "| " + " | ".join("---" for _ in range(max_cols)) + " |"
        row_strs = ["| " + " | ".join(c or " " for c in row) + " |" for row in data_rows]

        md_table = "\n".join([header_str, sep_str] + row_strs)

        structured = {
            "headers": headers,
            "rows": data_rows,
            "row_count": len(data_rows),
            "col_count": max_cols
        }

        return md_table, structured
