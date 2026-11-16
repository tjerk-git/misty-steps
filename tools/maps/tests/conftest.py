import sys
from pathlib import Path

# tools/maps is a folder of scripts, not a package: make `import provinces` work from the tests.
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
