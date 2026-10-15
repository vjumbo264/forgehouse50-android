# Verse-Level Headings Audit (Item 4)
Generated: 2026-10-15
Author: ForgeBuild Agent
Scope: VerseWell source databases (bible-sources/*.sqlite3), static mirror, and ForgeHouse 50 clients.

## 1. Problem Description
Some verses in Bible translations (most notably NIV, NKJV, NLT, GW, AMP, NLV, TPT) are preceded by section headings or titles (e.g., 'The Genealogy of Jesus the Messiah', 'John the Baptist Prepares the Way', 'The Baptism of Jesus').
Previously, these headings were either concatenated directly onto verse text (e.g. in NIV: 'John the Baptist Prepares the Way In those days John the Baptist came...') or in some cases stripped and discarded entirely.
This corrupted verse text, verse numbering, and note-taking/copying.

## 2. Root Cause in Source Representation
1. **Source SQLite Schema**:
   - `chapters` table contains HTML content with `<h2..4>` heading tags, e.g.:
     `<h3><span id="en-NIV-23194" class="text Matt-3-1">John the Baptist Prepares the Way<sup class='crossreference' value='(<a href="#cen-NIV-23194A" title="See cross-reference A">A</a>)'></sup></span></h3>`
   - `verses` table contains `unformatted` text where headings were prepended, e.g.:
     `'John the Baptist Prepares the Way
In those days John the Baptist came...'`
2. **VerseWell Importer Bug**:
   - In `versewell/scripts/import.py`, `heading_sets()` used regex `re.sub(r"<[^>]+>", "", m.group(1))` without first removing inner `<sup class="crossreference">...</sup>` blocks.
   - Because the inner tags contained cross-reference letters like `(A)`, stripping tags left trailing text like `"John the Baptist Prepares the WayA)'>"`.
   - As a result, `strip_heading()` failed exact matching against `verses.unformatted` and left headings concatenated into verse text.
   - Furthermore, `strip_heading()` only stripped the heading rather than preserving it as a structured `heading_before` field.

## 3. Translation Heading Inventory
Audit across all 12 source SQLite databases in `bible-sources/`:
- **amp.sqlite3**: 2,438 headings in chapters | 2,300 headings extracted
- **cev.sqlite3**: 3,029 headings in chapters | 1 heading extracted (most CEV section headings are in book intro chapters)
- **gw.sqlite3**: 2,515 headings in chapters | 2,341 headings extracted
- **kjv.sqlite3**: 1,351 headings in chapters | 17 headings extracted (mostly book subscriptions / special sections)
- **msg.sqlite3**: 1,586 headings in chapters | 45 headings extracted
- **niv2011.sqlite3**: 2,275 headings in chapters | 2,134 headings extracted
- **nkjv.sqlite3**: 3,116 headings in chapters | 2,964 headings extracted
- **nlt.sqlite3**: 2,525 headings in chapters | 2,380 headings extracted
- **nlv.sqlite3**: 2,427 headings in chapters | 2,426 headings extracted
- **tlb.sqlite3**: 0 headings in chapters | 0 headings extracted
- **tpt.sqlite3**: 1,195 headings in chapters | 1,156 headings extracted
- **voice.sqlite3**: 303 headings in chapters | 180 headings extracted

Total headings extracted across all translations: **17,944 section headings**.

## 4. Example References
- **Matthew 1:1 (NIV)**:
  - Heading: "The Genealogy of Jesus the Messiah"
  - Verse text: "This is the genealogy of Jesus the Messiah the son of David, the son of Abraham:"
- **Matthew 3:1 (NIV)**:
  - Heading: "John the Baptist Prepares the Way"
  - Verse text: "In those days John the Baptist came, preaching in the wilderness of Judea"
- **Matthew 3:13 (NIV)**:
  - Heading: "The Baptism of Jesus"
  - Verse text: "Then Jesus came from Galilee to the Jordan to be baptized by John."
- **Matthew 4:1 (NIV)**:
  - Heading: "Jesus Is Tested in the Wilderness"
  - Verse text: "Then Jesus was led by the Spirit into the wilderness to be tempted by the devil."
- **Matthew 4:18 (NIV)**:
  - Heading: "Jesus Calls His First Disciples"
  - Verse text: "As Jesus was walking beside the Sea of Galilee, he saw two brothers..."
- **Matthew 4:1 (GW)**:
  - Heading: "The Temptation of Jesus"
  - Verse text: "Then the Spirit led Jesus into the desert to be tempted by the devil."
- **Genesis 1:1 (AMP / GW)**:
  - Heading: "The Creation"
  - Verse text: "In the beginning God created..."

## 5. Architectural Remediation
1. **VerseWell Core**:
   - `schema.sql`: Add `heading_before TEXT` to `verses` table.
   - `import.py`: Strip `<sup[^>]*>.*?</sup>` inside headings; extract `heading_before` for anchored verses or verses matching heading sets; emit `heading_before` column.
   - `generate_static.py`: Include `heading_before` in chapter JSON output if present.
2. **ForgeHouse 50 Backend & Bundle Generator**:
   - `build_bundles.mjs`: Extract section headings into `h` field of `BundledVerse` (`{ n, t, f, h }`).
   - `functions/lib/versewell.mjs`: Separate `heading_before` and clean `text` in `getPassage()`.
3. **Clients**:
   - Android (`ReadScreen.kt`): Render `heading_before` as a distinct `titleMedium` / `titleSmall` section heading above the verse.
   - Website (`ReaderView.jsx`): Render `heading_before` as a styled section heading above the verse item.
   - Ensure notes, copy, search, and quiz use verse text only.
