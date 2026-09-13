import { readFile, rm, writeFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import { spawnSync } from "node:child_process";
import { loadEnv } from "vite";
import { apiOrigin, hostingConfig } from "./hosting-config.mjs";

const frontend = fileURLToPath(new URL("../", import.meta.url));
const output = new URL("../../firebase.json", import.meta.url);
// A failed build must not leave a stale manifest available for deployment.
await rm(output, { force: true });
try {
  const origin = apiOrigin(loadEnv("hosting", frontend, "VITE_").VITE_API_BASE_URL);
  const template = JSON.parse(await readFile(new URL("../../firebase.template.json", import.meta.url), "utf8"));
  const config = hostingConfig(template, origin);
  const build = spawnSync(process.platform === "win32" ? "npm.cmd" : "npm",
    ["run", "build", "--", "--mode", "hosting"], {
      cwd: frontend, stdio: "inherit", env: { ...process.env, VITE_API_BASE_URL: origin },
    });
  if (build.error) throw build.error;
  if (build.status !== 0) throw new Error("Hosting build failed; firebase.json was not generated.");
  await writeFile(output, JSON.stringify(config, null, 2) + "\n");
  console.log("Hosting build complete: frontend/dist and generated firebase.json use the same API origin.");
} catch (error) {
  console.error(error.message);
  process.exitCode = 1;
}
