# AGV-Lab Frontend Architecture Rules

## Scope and technology
- Applies to the React + TypeScript + Vite frontend only.
- UI administration uses Ant Design; the 3D world uses Three.js / React Three Fiber.
- Keep a single frontend application (feature-based modular monolith). Do not introduce micro-frontends without explicit approval.
- Keep the existing code working; prefer small, incremental refactors.

## Directory ownership
```text
src/
  app/                     # app composition, router, global providers
  features/
    map-management/        # map list, creation, activation and metadata UI
      pages/
      components/
      api/
      hooks/
      types/
    map-editor/            # 3D world, editor tools, map rendering
      pages/
      scenes/
      objects/
      components/
      hooks/
      types/
      utils/
    fleet/                 # AGV monitoring and operations
    task/                  # task management
  shared/                  # truly cross-feature UI, hooks, types, utilities
    components/
    hooks/
    types/
    utils/
  lib/                     # HTTP client, WebSocket client, low-level adapters
  assets/                  # imported static assets
  styles/                  # global styles
  main.tsx
```
- Create only folders needed by actual code; do not generate empty scaffolding.
- `public/` remains for files served unchanged. Keep `compose.yaml` at its existing project location unless explicitly asked to move it.
- `src/app/` owns `App.tsx`, router, and global providers; don't duplicate app roots.

## Placement rules
1. Put feature-specific code inside its owning `src/features/<feature>/` folder.
2. Put code in `shared/` only after it is genuinely reused across at least two features, or is clearly generic and independent of feature business logic.
3. Put transport setup (Axios/fetch wrapper, WebSocket connection) in `lib/`; put feature-specific API calls in `features/<feature>/api/`.
4. Pages compose feature components and handle route-level concerns; they must not contain large database/API or 3D scene implementations.
5. Keep `FactoryScene.tsx` focused on scene composition. Put individual 3D objects in `map-editor/objects/` and editor interactions in feature hooks/components.
6. Keep backend DTO types separate from 3D rendering component props where their needs differ. Define explicit mapping functions instead of coupling API payloads to JSX.
7. Do not create a new abstraction, hook, context, state store, or utility solely to reduce a few lines of code. Prefer simple code first.

## Module boundaries
- A feature may import from `shared/`, `lib/`, and its own files.
- Avoid importing private files from another feature. When cross-feature integration is necessary, expose a small documented public API (for example `features/map-management/index.ts`) or move truly shared contracts to `shared/types/`.
- Avoid circular imports and giant barrel files.
- Keep map-management (Ant Design workflows) separate from map-editor (Three.js rendering and editing).

## State and data ownership
- Map definition (points, edges, racks, walls, docks) comes from RCS APIs; treat the backend as the source of truth for saved maps.
- AGV runtime state (position, battery, status) comes from realtime APIs/WebSocket and should be updated without rebuilding the entire static map.
- Editor-only state (selection, camera, current tool, unsaved changes) stays local to the editor or its dedicated state store.
- Do not duplicate entire map data in multiple unrelated stores. Avoid unnecessary React re-renders for high-frequency AGV updates.
- Never hardcode map IDs, AGV positions, or warehouse objects as production data; use typed fixtures only for demos/tests.

## Naming
- React components/pages/scenes: PascalCase, e.g. `MapListPage.tsx`, `FactoryScene.tsx`, `Rack.tsx`.
- Hooks: `useXxx.ts`, e.g. `useMapEditor.ts`.
- API modules and utilities: camelCase, e.g. `mapApi.ts`, `mapMapper.ts`.
- Types: descriptive names, e.g. `MapDefinition`, `AgvRuntimeState`.
- Prefer consistent naming with the existing codebase over mass renaming.

## Agent change procedure
Before editing:
1. Inspect relevant existing files, imports, routes, API contracts, and current folder layout.
2. Identify the owning feature; reuse existing components and utilities.
3. For any change that moves many files, changes architecture, or adds a major dependency, propose a short plan and obtain approval.

While editing:
4. Change the smallest necessary set of files; do not create speculative folders/files.
5. Update imports and routes when moving files; preserve behavior.
6. Do not introduce micro-frontends, a new state-management framework, or a new UI framework without approval.
7. Keep 3D rendering, UI management, backend data, realtime state, and editor state responsibilities distinct.

After editing:
8. Run available type-check/build/lint commands appropriate to the change (`npm run build`, `npm run lint` when configured).
9. Report files changed, why they belong in those folders, and any checks not run.
10. Never claim checks passed unless they actually ran.

## Migration from current structure
- Move `src/components/objects/{Agv,Barrier,Dock,Pallet,Rack}.tsx` to `src/features/map-editor/objects/` when refactoring the editor.
- Move `src/scenes/FactoryScene.tsx` to `src/features/map-editor/scenes/`.
- Introduce `src/features/map-management/` when building the Ant Design map list and management screens.
- Update all imports; verify Vite build. Do not relocate files solely to match an ideal tree if it risks breaking working code.
