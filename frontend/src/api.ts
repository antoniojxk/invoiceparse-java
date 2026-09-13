import type { ApiError, ParseDocumentResponse } from "./types";

// Empty in the bundled build and Vite dev server; absolute in the Hosting build.
const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL ?? "").replace(/\/+$/, "");

export async function parseInvoice(file: File): Promise<ParseDocumentResponse> {
  const body = new FormData();
  body.append("file", file);
  const response = await fetch(`${API_BASE_URL}/api/v1/documents/parse`, { method: "POST", body, signal: AbortSignal.timeout(120_000) });

  if (!response.ok) {
    let error: ApiError = {};
    try {
      error = (await response.json()) as ApiError;
    } catch {
      // The fallback also covers non-JSON proxy and server errors.
    }
    throw new Error(error.message ?? "The invoice could not be processed. Please try another file.");
  }
  return response.json() as Promise<ParseDocumentResponse>;
}

export async function getApiHealth(signal?: AbortSignal): Promise<boolean> {
  try {
    const timeout = AbortSignal.timeout(60_000);
    const response = await fetch(`${API_BASE_URL}/actuator/health`, {
      signal: signal ? AbortSignal.any([signal, timeout]) : timeout,
      cache: "no-store",
    });
    if (!response.ok) return false;
    const health = await response.json() as { status?: string };
    return health.status === "UP";
  } catch {
    return false;
  }
}
