import { useEffect, useRef, useState } from "react";
import {
  AlertTriangle, ArrowLeft, Check, CheckCircle2, ChevronRight, CircleAlert, Clipboard,
  Download, FileCheck2, FileJson2, FileSearch, FileText, Image as ImageIcon,
  LoaderCircle, LockKeyhole, RotateCcw, ScanLine, ShieldCheck, Sparkles, UploadCloud, X,
} from "lucide-react";
import { getApiHealth, parseInvoice } from "./api";
import type { LineItem, ParseDocumentResponse, ValidationResult } from "./types";

const MAX_FILE_BYTES = 5 * 1024 * 1024;
const ACCEPTED_TYPES = ["application/pdf", "image/png", "image/jpeg"];
type Screen = "upload" | "processing" | "results";

const SAMPLE_INVOICES = [
  {
    name: "Text-layer invoice",
    description: "A one-page GST invoice with selectable text and itemized totals.",
    filename: "digital-invoice-layout-a.pdf",
    format: "PDF",
    detail: "1 page · 1.7 KB",
  },
  {
    name: "Raster invoice",
    description: "A high-resolution invoice image that exercises the OCR path.",
    filename: "image-invoice-layout-b.png",
    format: "PNG",
    detail: "1600 × 1200 · 75 KB",
  },
] as const;

function BrandMark() {
  return <div className="brand-mark" aria-hidden="true"><span className="brand-fold" /><span className="brand-line one" /><span className="brand-line two" /></div>;
}

export default function App() {
  const [screen, setScreen] = useState<Screen>("upload");
  const [file, setFile] = useState<File | null>(null);
  const [result, setResult] = useState<ParseDocumentResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [apiOnline, setApiOnline] = useState<boolean | null>(null);
  const [isDragging, setIsDragging] = useState(false);
  const [copied, setCopied] = useState(false);
  const [sampleLoading, setSampleLoading] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => { void getApiHealth().then(setApiOnline); }, []);

  function selectFile(nextFile: File) {
    let validationError: string | null = null;
    const hasAcceptedExtension = /\.(pdf|png|jpe?g)$/i.test(nextFile.name);
    if (!ACCEPTED_TYPES.includes(nextFile.type) && !hasAcceptedExtension) validationError = "Choose a PDF, PNG, JPG, or JPEG invoice.";
    else if (nextFile.size > MAX_FILE_BYTES) validationError = "This file is larger than the 15 MB upload limit.";
    else if (nextFile.size === 0) validationError = "This file is empty. Choose another invoice.";
    setError(validationError);
    setFile(validationError ? null : nextFile);
  }

  async function submit() {
    if (!file) return;
    setError(null);
    setScreen("processing");
    try {
      setResult(await parseInvoice(file));
      setScreen("results");
      setApiOnline(true);
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : "The invoice could not be processed.");
      setScreen("upload");
      void getApiHealth().then(setApiOnline);
    }
  }

  async function loadSample() {
    setError(null);
    setSampleLoading(true);
    try {
      const response = await fetch("/samples/digital-invoice-layout-a.pdf");
      if (!response.ok) throw new Error();
      const sample = new File([await response.blob()], "synthetic-sample-invoice.pdf", { type: "application/pdf" });
      selectFile(sample);
    } catch {
      setError("The synthetic sample could not be loaded. Please choose a file instead.");
    } finally {
      setSampleLoading(false);
    }
  }

  function reset() {
    setFile(null); setResult(null); setError(null); setCopied(false); setScreen("upload");
    if (inputRef.current) inputRef.current.value = "";
  }

  async function copyJson() {
    if (!result) return;
    await navigator.clipboard.writeText(JSON.stringify(result, null, 2));
    setCopied(true);
    window.setTimeout(() => setCopied(false), 1800);
  }

  function downloadJson() {
    if (!result) return;
    const url = URL.createObjectURL(new Blob([JSON.stringify(result, null, 2)], { type: "application/json" }));
    const link = document.createElement("a");
    link.href = url;
    link.download = `${result.originalFilename.replace(/\.[^.]+$/, "")}-parsed.json`;
    link.click();
    URL.revokeObjectURL(url);
  }

  return (
    <div className="app-shell">
      <header className="site-header">
        <button className="brand" type="button" onClick={reset} aria-label="InvoiceParse home">
          <BrandMark /><span><strong>InvoiceParse</strong><small>Document intelligence</small></span>
        </button>
        <div className={`api-status ${apiOnline === false ? "offline" : ""}`}>
          <span className="status-dot" />
          {apiOnline === null ? "Checking parser" : apiOnline ? "Parser ready" : "Parser offline"}
        </div>
      </header>

      <main>
        {screen === "upload" && <UploadScreen file={file} error={error} isDragging={isDragging} sampleLoading={sampleLoading} inputRef={inputRef} onFile={selectFile} onRemove={() => { setFile(null); setError(null); if (inputRef.current) inputRef.current.value = ""; }} onSubmit={submit} onSample={loadSample} onDragChange={setIsDragging} />}
        {screen === "processing" && file && <ProcessingScreen filename={file.name} />}
        {screen === "results" && result && <ResultsScreen result={result} copied={copied} onReset={reset} onCopy={copyJson} onDownload={downloadJson} />}
      </main>

      <footer className="site-footer">
        <span>Private demo container. No third-party AI service receives your document.</span>
        <span className="footer-tech">Spring Boot · PDFBox · Tesseract · React</span>
      </footer>
    </div>
  );
}

interface UploadScreenProps {
  file: File | null; error: string | null; isDragging: boolean; sampleLoading: boolean;
  inputRef: React.RefObject<HTMLInputElement>; onFile: (file: File) => void;
  onRemove: () => void; onSubmit: () => void; onSample: () => void; onDragChange: (dragging: boolean) => void;
}

function UploadScreen({ file, error, isDragging, sampleLoading, inputRef, onFile, onRemove, onSubmit, onSample, onDragChange }: UploadScreenProps) {
  return (
    <div className="upload-screen">
      <div className="upload-layout">
        <section className="intro-panel">
          <div className="eyebrow"><Sparkles size={14} /> Structured data, in seconds</div>
          <h1>Turn invoices into <em>review-ready</em> data.</h1>
          <p className="intro-copy">Upload a PDF or image. InvoiceParse detects text, runs OCR when needed, validates the totals, and returns clean, structured JSON.</p>
          <div className="process-list" aria-label="Processing pipeline">
            <ProcessStep icon={<FileSearch />} number="01" title="Detect" copy="Text PDF, scan, or image" />
            <ProcessStep icon={<ScanLine />} number="02" title="Extract" copy="Fields and line items" />
            <ProcessStep icon={<ShieldCheck />} number="03" title="Validate" copy="GSTIN, totals, confidence" />
          </div>
        </section>

        <section className="upload-panel" aria-labelledby="upload-title">
          <div className="upload-panel-heading"><div><span className="step-label">PUBLIC DEMO</span><h2 id="upload-title">Choose an invoice</h2></div><div className="privacy-chip"><LockKeyhole size={13} /> Ephemeral processing</div></div>
          <label
            className={`dropzone ${isDragging ? "is-dragging" : ""} ${file ? "has-file" : ""}`}
            onDragEnter={(event) => { event.preventDefault(); onDragChange(true); }}
            onDragOver={(event) => event.preventDefault()}
            onDragLeave={(event) => { event.preventDefault(); if (event.currentTarget === event.target) onDragChange(false); }}
            onDrop={(event) => { event.preventDefault(); onDragChange(false); const droppedFile = event.dataTransfer.files[0]; if (droppedFile) onFile(droppedFile); }}
          >
            <input ref={inputRef} type="file" accept=".pdf,.png,.jpg,.jpeg,application/pdf,image/png,image/jpeg" onChange={(event) => { const selected = event.target.files?.[0]; if (selected) onFile(selected); }} />
            {file ? (
              <div className="selected-file">
                <div className="file-icon"><FileText size={26} /></div>
                <div className="file-meta"><strong>{file.name}</strong><span>{formatBytes(file.size)} · Ready to parse</span></div>
                <button type="button" className="remove-file" aria-label={`Remove ${file.name}`} onClick={(event) => { event.preventDefault(); onRemove(); }}><X size={17} /></button>
              </div>
            ) : (
              <div className="dropzone-empty">
                <div className="upload-icon"><UploadCloud size={28} /></div><strong>Drop your invoice here</strong><span>or <u>browse files</u> from your computer</span>
                <div className="file-types"><span><FileText size={13} /> PDF</span><span><ImageIcon size={13} /> PNG</span><span><ImageIcon size={13} /> JPG</span><span>MAX 5 MB</span></div>
              </div>
            )}
          </label>
          {error && <div className="error-banner" role="alert"><CircleAlert size={17} /><span>{error}</span></div>}
          <button className="sample-button" type="button" disabled={sampleLoading} onClick={onSample}>
            {sampleLoading ? <LoaderCircle className="spinner" size={15} /> : <FileCheck2 size={15} />}
            {sampleLoading ? "Loading sample…" : "Use the synthetic sample"}
          </button>
          <button className="primary-button" type="button" disabled={!file} onClick={onSubmit}>Parse invoice <ChevronRight size={18} /></button>
          <p className="upload-note">Use synthetic or non-sensitive documents. Originals are discarded after processing; extracted results may remain in memory for up to one hour.</p>
        </section>
      </div>
      <SampleLibrary />
    </div>
  );
}

function SampleLibrary() {
  return (
    <section className="sample-library" aria-labelledby="sample-library-title">
      <div className="sample-library-heading">
        <div><span className="step-label">TEST DOCUMENTS</span><h2 id="sample-library-title">Sample invoices</h2></div>
        <p>Download a fixture, inspect it locally, then drop it into the uploader above to exercise a specific extraction path.</p>
      </div>
      <div className="sample-grid">
        {SAMPLE_INVOICES.map((sample) => (
          <article className="sample-card" key={sample.filename}>
            <div className={`sample-file-icon ${sample.format.toLowerCase()}`}>{sample.format === "PDF" ? <FileText size={24} /> : <ImageIcon size={24} />}</div>
            <div className="sample-card-copy">
              <div className="sample-card-meta"><span>{sample.format}</span><small>{sample.detail}</small></div>
              <h3>{sample.name}</h3>
              <p>{sample.description}</p>
              <code>{sample.filename}</code>
            </div>
            <a className="sample-download" href={`/samples/${sample.filename}`} download={sample.filename} aria-label={`Download ${sample.name}`}>
              <Download size={16} /> Download
            </a>
          </article>
        ))}
      </div>
      <p className="sample-disclaimer"><ShieldCheck size={14} /> Every name, address, identifier, and transaction in these files is synthetic.</p>
    </section>
  );
}

function ProcessStep({ icon, number, title, copy }: { icon: React.ReactNode; number: string; title: string; copy: string }) {
  return <div className="process-step"><div className="process-icon">{icon}</div><span className="process-number">{number}</span><div><strong>{title}</strong><small>{copy}</small></div></div>;
}

function ProcessingScreen({ filename }: { filename: string }) {
  const [activeStep, setActiveStep] = useState(0);
  const steps = ["Inspecting document", "Extracting content", "Validating results"];
  useEffect(() => { const timer = window.setInterval(() => setActiveStep((current) => Math.min(current + 1, steps.length - 1)), 1300); return () => window.clearInterval(timer); }, []);
  return (
    <section className="processing-card" aria-live="polite">
      <div className="processing-visual"><div className="scan-document"><span /><span /><span /><span /></div><div className="scan-beam" /></div>
      <span className="step-label">ANALYZING DOCUMENT</span><h1>Reading your invoice</h1><p title={filename}>{filename}</p>
      <div className="processing-steps">{steps.map((step, index) => <div className={index <= activeStep ? "active" : ""} key={step}>{index < activeStep ? <Check size={14} /> : index === activeStep ? <LoaderCircle className="spinner" size={14} /> : <span />}{step}</div>)}</div>
      <small>OCR can take a little longer for scanned, multi-page documents.</small>
    </section>
  );
}

interface ResultsScreenProps { result: ParseDocumentResponse; copied: boolean; onReset: () => void; onCopy: () => void; onDownload: () => void; }

function ResultsScreen({ result, copied, onReset, onCopy, onDownload }: ResultsScreenProps) {
  const confidence = Math.round(result.overallConfidence * 100);
  return (
    <div className="results-layout">
      <div className="results-toolbar">
        <button className="text-button" type="button" onClick={onReset}><ArrowLeft size={16} /> Parse another</button>
        <div className="toolbar-actions"><button className="secondary-button" type="button" onClick={onCopy}>{copied ? <Check size={16} /> : <Clipboard size={16} />} {copied ? "Copied" : "Copy JSON"}</button><button className="secondary-button dark" type="button" onClick={onDownload}><Download size={16} /> Export JSON</button></div>
      </div>

      <section className="result-summary">
        <div className="result-file-icon"><FileCheck2 size={26} /></div>
        <div className="result-title"><span className="step-label">PARSE COMPLETE</span><h1>{result.originalFilename}</h1><div className="result-meta"><span>{sourceLabel(result.sourceType)}</span><i /><span>{result.pageCount} {result.pageCount === 1 ? "page" : "pages"}</span>{result.duplicate && <><i /><span>Duplicate detected</span></>}</div></div>
        <div className={`review-status ${result.manualReviewRequired ? "review" : "verified"}`}>{result.manualReviewRequired ? <AlertTriangle size={18} /> : <CheckCircle2 size={18} />}<span><small>STATUS</small>{result.manualReviewRequired ? "Review suggested" : "Validation passed"}</span></div>
        <div className="confidence-block"><div className="confidence-ring" style={{ "--confidence": `${confidence * 3.6}deg` } as React.CSSProperties}><strong>{confidence}%</strong></div><span><small>OVERALL</small>Confidence</span></div>
      </section>

      {result.warnings.length > 0 && <section className="warnings-panel"><AlertTriangle size={18} /><div><strong>Review these items</strong>{result.warnings.map((warning) => <p key={warning}>{warning}</p>)}</div></section>}

      <div className="result-grid">
        <section className="data-card details-card">
          <CardHeading icon={<FileText />} title="Invoice details" eyebrow="EXTRACTED FIELDS" />
          <div className="detail-grid">
            <DataField label="Invoice number" value={result.invoiceNumber} confidence={result.fieldConfidences.invoiceNumber} />
            <DataField label="Invoice date" value={formatDate(result.invoiceDate)} confidence={result.fieldConfidences.invoiceDate} />
            <DataField label="Supplier" value={result.supplierName} confidence={result.fieldConfidences.supplierName} />
            <DataField label="Supplier GSTIN" value={result.supplierGstin} confidence={result.fieldConfidences.supplierGstin} mono />
            <DataField label="Customer" value={result.customerName} confidence={result.fieldConfidences.customerName} />
            <DataField label="Customer GSTIN" value={result.customerGstin} confidence={result.fieldConfidences.customerGstin} mono />
            <DataField label="Billing address" value={result.address} confidence={result.fieldConfidences.address} wide />
          </div>
        </section>
        <section className="data-card amounts-card">
          <CardHeading icon={<FileJson2 />} title="Amount summary" eyebrow="NORMALIZED TOTALS" />
          <div className="amount-list"><AmountRow label="Subtotal" value={result.subtotal} currency={result.currency} /><AmountRow label="Discount" value={result.discount} currency={result.currency} /><AmountRow label="Taxable amount" value={result.taxableAmount} currency={result.currency} /><AmountRow label="CGST" value={result.cgst} currency={result.currency} /><AmountRow label="SGST" value={result.sgst} currency={result.currency} /><AmountRow label="IGST" value={result.igst} currency={result.currency} /><AmountRow label="Round off" value={result.roundOff} currency={result.currency} /><div className="grand-total"><span>Grand total</span><strong>{formatMoney(result.grandTotal, result.currency)}</strong></div></div>
        </section>
      </div>

      <section className="data-card table-card"><CardHeading icon={<ScanLine />} title="Line items" eyebrow={`${result.lineItems.length} ITEMS DETECTED`} />{result.lineItems.length > 0 ? <LineItemsTable items={result.lineItems} currency={result.currency} /> : <EmptyState copy="No line items were confidently detected." />}</section>
      <section className="data-card validation-card"><CardHeading icon={<ShieldCheck />} title="Validation checks" eyebrow="ARITHMETIC & FORMAT" />{result.validationResults.length > 0 ? <div className="validation-list">{result.validationResults.map((validation, index) => <ValidationRow validation={validation} key={`${validation.code}-${index}`} />)}</div> : <EmptyState copy="No validation checks were returned." />}</section>
      <div className="result-end"><p><strong>Document ID</strong> {result.documentId}</p><button className="text-button" type="button" onClick={onReset}><RotateCcw size={15} /> Parse another invoice</button></div>
    </div>
  );
}

function CardHeading({ icon, title, eyebrow }: { icon: React.ReactNode; title: string; eyebrow: string }) {
  return <div className="card-heading"><div className="card-icon">{icon}</div><div><span>{eyebrow}</span><h2>{title}</h2></div></div>;
}

function DataField({ label, value, confidence, mono = false, wide = false }: { label: string; value: string | null; confidence?: number; mono?: boolean; wide?: boolean }) {
  return <div className={`data-field ${wide ? "wide" : ""}`}><span>{label}{confidence !== undefined && <ConfidenceDot value={confidence} />}</span><strong className={mono ? "mono" : ""}>{value || "Not detected"}</strong></div>;
}

function ConfidenceDot({ value }: { value: number }) {
  const tone = value >= .85 ? "high" : value >= .7 ? "medium" : "low";
  return <i className={`confidence-dot ${tone}`} title={`${Math.round(value * 100)}% field confidence`} />;
}

function AmountRow({ label, value, currency }: { label: string; value: number | null; currency: string | null }) {
  return <div><span>{label}</span><strong>{formatMoney(value, currency)}</strong></div>;
}

function LineItemsTable({ items, currency }: { items: LineItem[]; currency: string | null }) {
  return <div className="table-scroll"><table><thead><tr><th>Item</th><th>HSN / SAC</th><th>Qty</th><th>Rate</th><th>GST</th><th className="align-right">Amount</th><th><span className="sr-only">Confidence</span></th></tr></thead><tbody>{items.map((item, index) => <tr key={`${item.productName}-${index}`}><td><strong>{item.productName || item.description || `Item ${index + 1}`}</strong>{item.productName && item.description && item.productName !== item.description && <small>{item.description}</small>}</td><td className="mono">{item.hsnSac || "—"}</td><td>{item.quantity ?? "—"}{item.unit ? ` ${item.unit}` : ""}</td><td>{formatMoney(item.unitRate, currency)}</td><td>{item.gstPercentage !== null ? `${item.gstPercentage}%` : "—"}</td><td className="align-right"><strong>{formatMoney(item.lineTotal, currency)}</strong></td><td><ConfidenceDot value={item.confidence} /></td></tr>)}</tbody></table></div>;
}

function ValidationRow({ validation }: { validation: ValidationResult }) {
  return <div className={validation.valid ? "valid" : "invalid"}>{validation.valid ? <CheckCircle2 size={18} /> : <AlertTriangle size={18} />}<span><strong>{humanizeCode(validation.code)}</strong><small>{validation.message}</small></span><code>{validation.field}</code></div>;
}

function EmptyState({ copy }: { copy: string }) { return <div className="empty-state"><FileSearch size={20} /><span>{copy}</span></div>; }
function formatBytes(bytes: number) { return bytes < 1024 * 1024 ? `${Math.max(1, Math.round(bytes / 1024))} KB` : `${(bytes / (1024 * 1024)).toFixed(1)} MB`; }
function formatDate(value: string | null) { if (!value) return null; const date = new Date(`${value}T00:00:00`); return Number.isNaN(date.getTime()) ? value : new Intl.DateTimeFormat("en", { day: "2-digit", month: "short", year: "numeric" }).format(date); }
function formatMoney(value: number | null, currency: string | null) { if (value === null || value === undefined) return "—"; try { return new Intl.NumberFormat("en-IN", { style: "currency", currency: currency || "INR", minimumFractionDigits: 2 }).format(value); } catch { return `${currency || ""} ${value.toFixed(2)}`.trim(); } }
function sourceLabel(source: ParseDocumentResponse["sourceType"]) { return { DIGITAL_PDF: "Digital PDF", SCANNED_PDF: "Scanned PDF · OCR", IMAGE: "Image · OCR" }[source]; }
function humanizeCode(code: string) { return code.toLowerCase().split("_").map((word) => word.charAt(0).toUpperCase() + word.slice(1)).join(" "); }
