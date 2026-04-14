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
