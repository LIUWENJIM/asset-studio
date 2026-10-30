import type { Metadata } from "next";
import { JobsProvider } from "@/lib/jobs-provider";
import { SiteHeader } from "@/components/site-header";
import "./globals.css";

export const metadata: Metadata = {
  title: "Asset Studio · 素材工坊",
  description: "浏览模板、上传图片，后台异步生成你的专属素材。",
};

export default function RootLayout({
  children,
}: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="zh-CN">
      <body className="min-h-dvh bg-canvas font-sans text-ink antialiased">
        <JobsProvider>
          <SiteHeader />
          <main className="mx-auto w-full max-w-[1400px] px-5 pb-16">{children}</main>
        </JobsProvider>
      </body>
    </html>
  );
}