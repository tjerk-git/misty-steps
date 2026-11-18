import sys
from pathlib import Path

# tools/maps is a folder of scripts, not a package: make `import provinces` and `import build_style` work.
HERE = Path(__file__).resolve().parent.parent
for path in (HERE, HERE / "style"):
    sys.path.insert(0, str(path))
