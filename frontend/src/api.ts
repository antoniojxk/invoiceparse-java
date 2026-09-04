import type { ApiError, ParseDocumentResponse } from "./types";

export async function parseInvoice(file: File): Promise<ParseDocumentResponse> {
  const body = new FormData();
  body.append("file", file);
  const response = await fetch("/api/v1/documents/parse", { method: "POST", body });

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

export async function getApiHealth(): Promise<boolean> {
  try {
    const response = await fetch("/actuator/health", { signal: AbortSignal.timeout(2500) });
    return response.ok;
  } catch {
    return false;
  }
}
