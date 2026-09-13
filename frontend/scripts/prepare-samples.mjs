import { copyFile, mkdir } from "node:fs/promises";
const destination = new URL("../public/samples/", import.meta.url);
await mkdir(destination, { recursive: true });
for (const name of ["digital-invoice-layout-a.pdf", "image-invoice-layout-b.png"]) {
  await copyFile(new URL(`../../samples/${name}`, import.meta.url), new URL(name, destination));
}
