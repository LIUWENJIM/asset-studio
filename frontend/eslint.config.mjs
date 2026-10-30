import nextCoreWebVitals from "eslint-config-next/core-web-vitals";
import nextTypescript from "eslint-config-next/typescript";

// eslint-config-next 16 自带 flat config，直接展开即可，
// 不需要再用 FlatCompat 转 legacy 配置（那样会触发 config-validator 的循环引用报错）。
const config = [
  {
    ignores: [
      ".next/**",
      "node_modules/**",
      "next-env.d.ts",
      "coverage/**",
      "out/**",
    ],
  },
  ...nextCoreWebVitals,
  ...nextTypescript,
];

export default config;