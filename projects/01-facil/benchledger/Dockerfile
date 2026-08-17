FROM node:22.23.1-alpine

RUN corepack enable && corepack prepare pnpm@11.22.0 --activate

WORKDIR /workspace

COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
COPY apps/api/package.json apps/api/package.json
COPY apps/web/package.json apps/web/package.json
COPY packages/domain/package.json packages/domain/package.json

RUN pnpm install --frozen-lockfile

COPY . .

RUN pnpm --filter @benchledger/domain build && \
    DATABASE_URL=postgresql://benchledger:benchledger_local@db:5432/benchledger \
    pnpm --filter @benchledger/api prisma:generate

EXPOSE 3000 5173
