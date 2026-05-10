import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  output: "standalone",
  images: {
    remotePatterns: [
      {
        protocol: "https",
        hostname: "xabewjiiewyhhjfekazv.supabase.co",
        pathname: "/storage/v1/object/public/**",
      },
    ],
  },
  async rewrites() {
    // In production, nginx handles proxying; dev rewrites target local services.
    if (process.env.NODE_ENV === "production") return [];

    const backendUrl = process.env.BACKEND_URL || "http://localhost:8080";
    const backendUserUrl = process.env.BACKEND_USER_URL || "http://localhost:8081";
    const backendChatUrl = process.env.BACKEND_CHAT_URL || "http://localhost:8082";
    const backendDinnerUrl = process.env.BACKEND_DINNER_URL || "http://localhost:8083";
    const backendBpostUrl = process.env.BACKEND_BPOST_URL || "http://localhost:8084";

    return [
      {
        source: "/v1/api/user/:path*",
        destination: `${backendUserUrl}/v1/api/user/:path*`,
      },
      {
        source: "/v1/api/chat-app/:path*",
        destination: `${backendChatUrl}/v1/api/chat-app/:path*`,
      },
      {
        source: "/v1/api/dinner/:path*",
        destination: `${backendDinnerUrl}/v1/api/dinner/:path*`,
      },
      {
        source: "/v1/api/b-post/:path*",
        destination: `${backendBpostUrl}/v1/api/b-post/:path*`,
      },
      {
        source: "/v1/api/:path*",
        destination: `${backendUrl}/v1/api/:path*`,
      },
    ];
  },
};

export default nextConfig;
