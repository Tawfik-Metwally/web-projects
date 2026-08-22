FROM node:22.23.1-alpine@sha256:16e22a550f3863206a3f701448c45f7912c6896a62de43add43bb9c86130c3e2

RUN corepack enable && corepack prepare pnpm@11.22.0 --activate

WORKDIR /workspace
RUN chown node:node /workspace
USER node

COPY --chown=node:node package.json pnpm-lock.yaml pnpm-workspace.yaml ./
COPY --chown=node:node apps/api/package.json apps/api/package.json
COPY --chown=node:node apps/web/package.json apps/web/package.json
COPY --chown=node:node packages/domain/package.json packages/domain/package.json

RUN pnpm install --frozen-lockfile

COPY --chown=node:node . .

RUN pnpm --filter @benchledger/domain build && \
    DATABASE_URL=postgresql://benchledger:benchledger_local@db:5432/benchledger \
    pnpm --filter @benchledger/api prisma:generate

EXPOSE 3000 5173
