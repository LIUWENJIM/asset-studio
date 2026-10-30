import type { NextConfig } from "next";

/**
 * 浏览器只访问同源 /api 与 /files，由 Next 透传到 Java 后端。
 * 这样 Cookie 是第一方、无需 CORS，容器内用服务名即可。
 * Next 在这里只做转发，不含任何业务逻辑。
 */
const backendOrigin = process.env.BACKEND_ORIGIN ?? "http://localhost:8080";

const nextConfig: NextConfig = {
  output: "standalone",
  async rewrites() {
    return [
      { source: "/api/:path*", destination: `${backendOrigin}/api/:path*` },
      { source: "/files/:path*", destination: `${backendOrigin}/files/:path*` },
      { source: "/media/:path*", destination: `${backendOrigin}/media/:path*` },
    ];
  },
};

export default nextConfig;