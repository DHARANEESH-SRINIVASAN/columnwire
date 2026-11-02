import { fileURLToPath } from "node:url";
import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

const source = (file: string) => fileURLToPath(new URL(`../packages/columnwire/src/${file}`, import.meta.url));

export default defineConfig({
  base: process.env.VITE_BASE ?? "/",
  plugins: [react()],
  resolve: {
    alias: [
      { find: /^columnwire\/react$/, replacement: source("react.tsx") },
      { find: /^columnwire$/, replacement: source("index.ts") },
    ],
  },
  server: {
    proxy: {
      "/api": "http://localhost:8080",
    },
  },
});
