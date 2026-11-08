import os
import shutil
import tempfile
import zipfile
from typing import List, Optional, Tuple


class ArchiveSecurityException(Exception):
    """Raised when an archive contains unsafe paths (e.g. zip-slip attempt)."""
    pass


class ArchiveParser:
    @staticmethod
    def extract_safely(zip_path: str, target_dir: Optional[str] = None) -> Tuple[str, List[str]]:
        """
        Extracts zip file safely into target_dir (or a created temp directory).
        Prevents Zip-Slip vulnerability.
        Returns (extract_dir, list_of_extracted_file_paths).
        """
        if not os.path.exists(zip_path):
            raise FileNotFoundError(f"Archive file not found: {zip_path}")

        if target_dir is None:
            extract_dir = tempfile.mkdtemp(prefix="bap_zip_")
        else:
            extract_dir = target_dir
            os.makedirs(extract_dir, exist_ok=True)

        extracted_files: List[str] = []
        resolved_extract_dir = os.path.abspath(extract_dir)

        with zipfile.ZipFile(zip_path, "r") as archive:
            for member in archive.infolist():
                # Check for zip-slip attempt
                member_path = member.filename
                destination_path = os.path.abspath(os.path.join(resolved_extract_dir, member_path))

                # Must start with resolved_extract_dir + separator
                if not destination_path.startswith(resolved_extract_dir + os.sep) and destination_path != resolved_extract_dir:
                    raise ArchiveSecurityException(
                        f"Zip slip attempt detected in {zip_path}: {member_path}"
                    )

                # Skip directories
                if member.is_dir() or member.filename.endswith("/"):
                    os.makedirs(destination_path, exist_ok=True)
                    continue

                # Ensure parent dir exists
                os.makedirs(os.path.dirname(destination_path), exist_ok=True)

                # Extract file
                with archive.open(member) as source, open(destination_path, "wb") as target:
                    shutil.copyfileobj(source, target)

                # Skip macOS metadata files
                base_name = os.path.basename(destination_path)
                if not base_name.startswith("._") and not base_name.startswith(".DS_Store"):
                    extracted_files.append(destination_path)

        return resolved_extract_dir, extracted_files
