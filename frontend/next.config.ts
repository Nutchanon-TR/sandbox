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
    // In production, nginx handles proxying — rewrites here would fail
    // because there's no local backend at localhost:8080 inside the container
    if (process.env.NODE_ENV === "production") return [];
    const backendUrl = process.env.BACKEND_URL || "http://localhost:8080";
    return [
      {
        source: "/v1/api/:path*",
        destination: `${backendUrl}/v1/api/:path*`,
      },
    ];
  },
};

export default nextConfig;
