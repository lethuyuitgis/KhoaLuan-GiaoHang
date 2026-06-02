# Frontend testing — Vitest

The three frontend packages (`@shop/shared`, `@shop/miniapp`, `@shop/webadmin`)
each have a Vitest setup. The split keeps each package's environment focused:

| Package        | Env    | Sample suites                                  |
| -------------- | ------ | ---------------------------------------------- |
| `@shop/shared` | node   | `format-money`, `format-date` (pure helpers)   |
| `@shop/miniapp`| jsdom  | `cart-store` (Zustand store)                   |
| `@shop/webadmin`| jsdom | `auth-store`, `OrderStatusBadge` (component)   |

## Running

```bash
# run every package's suite (parallel)
pnpm -r test

# watch mode in a single package
cd frontend/shared    && pnpm test:watch
cd frontend/miniapp   && pnpm test:watch
cd frontend/webadmin  && pnpm test:watch

# UI mode (vitest --ui) is also available via the dev dep
pnpm --filter @shop/shared exec vitest --ui
```

## Pinned to Vitest 2.x

We are on Vite 5. Vitest 3+ requires Vite 6+, so the workspace pins
`vitest@^2` and `@vitest/ui@^2`. When the frontend upgrades to Vite 6/7,
bump these together.

## Conventions

- Test files live next to source: `foo.ts` → `foo.test.ts`, never in a
  parallel `__tests__/` tree.
- DOM-touching tests import `@testing-library/react` matchers via
  `test-setup.ts` (registered in each `vitest.config.ts` under `setupFiles`).
- Tests do not import from build output — only from source paths exposed by
  workspace `exports`.
