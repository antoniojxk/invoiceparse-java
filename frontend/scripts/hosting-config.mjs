export function apiOrigin(value) {
  if (!value?.trim()) {
    throw new Error("VITE_API_BASE_URL is required for Hosting. Set it in frontend/.env.hosting.local or the CI build environment.");
  }
  let url;
  try { url = new URL(value.trim()); }
  catch { throw new Error("VITE_API_BASE_URL must be a valid HTTPS origin."); }
  if (url.protocol !== "https:" || url.username || url.password || url.pathname !== "/" || url.search || url.hash) {
    throw new Error("VITE_API_BASE_URL must be an HTTPS origin without credentials, a path, query, or fragment.");
  }
  return url.origin;
}

export function hostingConfig(template, origin) {
  const config = structuredClone(template);
  let replacements = 0;
  for (const group of config.hosting.headers) {
    for (const header of group.headers) {
      if (header.key === "Content-Security-Policy" && header.value.includes("__API_ORIGIN__")) {
        header.value = header.value.replaceAll("__API_ORIGIN__", apiOrigin(origin));
        replacements++;
      }
    }
  }
  if (replacements !== 1) throw new Error("Expected exactly one CSP API origin placeholder in firebase.template.json.");
  return config;
}
