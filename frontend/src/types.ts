export interface LineItem {
  productName: string | null;
  description: string | null;
  hsnSac: string | null;
  batchNumber: string | null;
  expiryDate: string | null;
  quantity: number | null;
  unit: string | null;
  unitRate: number | null;
  discount: number | null;
  gstPercentage: number | null;
  taxableAmount: number | null;
  lineTotal: number | null;
  confidence: number;
}

export interface ValidationResult {
  code: string;
  field: string;
  valid: boolean;
  message: string;
}

export interface ParseDocumentResponse {
  documentId: string;
  originalFilename: string;
  fileHash: string;
  duplicate: boolean;
  documentType: string;
  sourceType: "DIGITAL_PDF" | "SCANNED_PDF" | "IMAGE";
  pageCount: number;
  invoiceNumber: string | null;
  invoiceDate: string | null;
  supplierName: string | null;
  supplierGstin: string | null;
  customerName: string | null;
  customerGstin: string | null;
  address: string | null;
  subtotal: number | null;
  discount: number | null;
  cgst: number | null;
  sgst: number | null;
  igst: number | null;
  taxableAmount: number | null;
  roundOff: number | null;
  grandTotal: number | null;
  currency: string | null;
  lineItems: LineItem[];
  validationResults: ValidationResult[];
  fieldConfidences: Record<string, number>;
  overallConfidence: number;
  manualReviewRequired: boolean;
  warnings: string[];
}

export interface ApiError {
  code?: string;
  message?: string;
}
