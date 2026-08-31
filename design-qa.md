# Project List Design QA

- Source visual truth: `C:\Users\53737\AppData\Local\Temp\codex-clipboard-3255ef33-9f1d-417c-84a0-6ddb51ba8bab.png`
- Implementation route: `http://127.0.0.1:8088/projects`
- Implementation screenshot: unavailable (the Codex in-app browser automation surface was not available in this text session)
- Reference viewport/pixels: 3288 × 1978 pixels; desktop browser state; density not supplied
- Comparison CSS viewport: unavailable
- State: authenticated project list, card arrangement, default project-scope filter, cover hover state required

**Full-view comparison evidence**

- The source image was opened at original resolution and used to match the content order: project name/status, category, description, date, and actions.
- The implementation is running, but a browser-rendered screenshot could not be captured. A visual side-by-side comparison therefore cannot be claimed.

**Focused region comparison evidence**

- Source regions inspected: list header/tool row, three-card row, card header/status, category and description, footer actions, and pagination.
- Implementation regions could not be captured. The hover cover popover and responsive states remain visually unverified.

**Findings**

- [P2] Browser-rendered visual evidence is missing.
  - Location: `/projects`, full view and project-card hover state.
  - Evidence: the source screenshot is available, while no implementation screenshot can be captured through the current in-app browser surface.
  - Impact: typography, exact spacing, popover placement, and responsive wrapping cannot be compared with pixel-level confidence.
  - Fix: capture `/projects` at the same desktop viewport, capture one card's hover-cover state, and compare both images against the source.

**Required fidelity surfaces**

- Fonts and typography: implemented using the application's existing typography tokens; browser rendering not visually verified.
- Spacing and layout rhythm: card information order and proportions were adjusted to the source; exact pixel comparison is blocked.
- Colors and visual tokens: existing green/neutral application tokens preserved; browser rendering not visually verified.
- Image quality and asset fidelity: project covers are real authenticated uploads rendered with `object-fit: cover`; crop and sharpness need browser capture.
- Copy and content: includes `全部项目`, `我创建的`, `我参与的`, `已完成的`, a read-only hover cover preview, and the source card content order.

**Interaction and functional checks**

- Frontend production build: passed.
- Backend classes and repository query compilation: passed.
- Database migration V14: applied successfully.
- Authenticated project list queries: passed.
- Created/completed filters: passed with live data.
- Participated filter query: executed successfully; current admin data has no participated-only projects.
- Project create → cover upload → authenticated cover read → completed filter → cleanup: passed.
- Project create → cover upload → information edit → cover association retained → cover removal → cleanup: passed.
- Roles with project create/edit permission but without cover-manage permission: 0.
- Database migration V15: applied successfully.
- Unauthorized cover access: returned HTTP 401.
- Temporary verification data remaining: 0 projects.
- Console errors: not checked because browser automation was unavailable.

**Comparison history**

- Iteration 1: source layout translated into the existing Vue/Element Plus project list; card content order was simplified and aligned with the screenshot.
- Iteration 2: added authenticated cover upload/read/remove, hover preview, create-dialog cover selection, table cover thumbnail, and project-scope filters.
- Iteration 3: frontend build, backend compile/boot, migration, permission, filter, and cover lifecycle checks passed. Visual comparison remains blocked by unavailable browser capture.
- Iteration 4: removed the ambiguous card-footer `封面` action, added a clear `更新封面` action inside the hover preview, and connected it directly to the native image picker and authenticated cover upload flow.

**Implementation checklist**

- Capture the full `/projects` page at a matching viewport.
- Capture a project card with the cover hover popover open.
- Check console errors and responsive behavior.
- Fix any remaining P2 differences, then repeat the comparison.

**Follow-up polish**

- Fine-tune popover direction near the right edge after a real browser capture.

final result: blocked
