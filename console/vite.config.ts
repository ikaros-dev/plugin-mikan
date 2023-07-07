import { defineConfig } from 'vite'
import { fileURLToPath, URL } from "url";
import Vue from "@vitejs/plugin-vue";
import VueJsx from "@vitejs/plugin-vue-jsx";

// Current plugin entry name value must eq plugin.yaml name value.
const pluginEntryName = "PluginStarter";
// https://vitejs.dev/config/
export default defineConfig({
  define: {
    'process.env': {},
  },
  plugins: [Vue(), VueJsx()],
  resolve: {
    alias: {
      "@": fileURLToPath(new URL("./src", import.meta.url)),
    },
  },
  build: {
    outDir: fileURLToPath(
        new URL("../src/main/resources/console", import.meta.url)
    ),
    emptyOutDir: true,
    lib: {
      entry: "src/index.ts",
      name: pluginEntryName,
      formats: ["iife"],
      fileName: () => "index.js",
    },
  },
})
