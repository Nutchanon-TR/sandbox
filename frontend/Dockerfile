# Stage 1: Install dependencies and build
FROM node:20-alpine AS builder
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
# Set environment variables if needed during build
# ENV NEXT_PUBLIC_API_URL=https://your-backend-url
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

EXPOSE 3000
# The server.js is created by Next.js standalone mode
CMD ["node", "server.js"]
