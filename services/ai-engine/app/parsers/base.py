from abc import ABC, abstractmethod
from dataclasses import dataclass, field
from typing import Any, Dict, List, Optional
from PIL import Image


@dataclass
class ParsedDocument:
    text: str
    tables: List[Dict[str, Any]] = field(default_factory=list)
    page_count: int = 1
    ocr_confidence: float = 0.98
    is_scanned: bool = False
    page_images: List[Image.Image] = field(default_factory=list)
    metadata: Dict[str, Any] = field(default_factory=dict)


class BaseParser(ABC):
    @abstractmethod
    def parse(self, file_path: str, **kwargs) -> ParsedDocument:
        """Parse file at file_path and return structured ParsedDocument."""
        pass
