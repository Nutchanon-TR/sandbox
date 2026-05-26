# Stage 1: Install dependencies and build
FROM node:20-alpine AS builder
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
# NEXT_PUBLIC_* vars must be available at build time (baked into Next.js bundle)
ARG NEXT_PUBLIC_SUPABASE_URL
ARG NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY
ARG NEXT_PUBLIC_SITE_URL
ARG NEXT_PUBLIC_PERSONA_FEED_DEV_ACTIONS_ENABLED
ENV NEXT_PUBLIC_SUPABASE_URL=$NEXT_PUBLIC_SUPABASE_URL
ENV NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY=$NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY
ENV NEXT_PUBLIC_SITE_URL=$NEXT_PUBLIC_SITE_URL
ENV NEXT_PUBLIC_PERSONA_FEED_DEV_ACTIONS_ENABLED=$NEXT_PUBLIC_PERSONA_FEED_DEV_ACTIONS_ENABLED
RUN npm run build

# Stage 2: Production runtime environment
FROM node:20-alpine AS runner
WORKDIR /app
ENV NODE_ENV=production

# Next.js standalone output recommendation (requires output: 'standalone' in next.config.ts)
# If you don't use 'standalone', we copy everything needed to run `npm start`
COPY --from=builder /app/public ./public
COPY --from=builder /app/.next/standalone ./
COPY --from=builder /app/.next/static ./.next/static

# If you haven't configured Next.js standalone mode, use this approach instead:
# COPY --from=builder /app/package.json ./package.json
# COPY --from=builder /app/node_modules ./node_modules
# COPY --from=builder /app/.next ./.next
# COPY --from=builder /app/public ./public
# CMD ["npm", "start"]

ARG EXPOSE_PORT=3000
EXPOSE ${EXPOSE_PORT}
# The server.js is created by Next.js standalone mode
CMD ["node", "server.js"]
