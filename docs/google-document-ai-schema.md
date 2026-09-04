# Google Document AI Custom Extractor schema

Create a **Custom Extractor** processor and use the field names below verbatim. The Java mapper accepts a few aliases from Google's pretrained Invoice Parser, but these names are the tested contract for this application.

## Document prompt

> Extract accounting-document fields only when they are supported by text or visual evidence in the document. Do not invent missing values. Identify parties by their semantic roles, labels, tax identifiers, and surrounding context rather than by left/right or top/bottom position. The supplier is the party supplying goods or services; the customer is the buyer or recipient. For purchase orders, the issuer is usually the customer and the named party/vendor is the supplier. Return monetary values as numbers without currency symbols or thousands separators. Return percentages as numbers without the percent sign. Preserve identifiers exactly except for surrounding whitespace. Return each product row as one `line_items` entity. A blank cell is not zero: omit the field when the document does not provide it.

## Top-level fields

| Field | Type | Description |
|---|---|---|
| `document_type` | string | One of `invoice`, `purchase_order`, `sales_order`, or `purchase_bill` based on the document title and business meaning. |
| `invoice_number` | string | Invoice, bill, or order identifier belonging to the document being parsed, not a referenced purchase-order number. |
| `invoice_date` | date | Issue date of the current document. |
| `supplier_name` | string | Legal or trading name of the party supplying the goods or services. Do not choose by page position alone. |
| `supplier_gstin` | string | Fifteen-character Indian GSTIN belonging to the supplier. |
| `customer_name` | string | Legal or trading name of the buyer, bill-to party, or recipient. |
| `customer_gstin` | string | Fifteen-character Indian GSTIN belonging to the customer. |
| `supplier_address` | address/string | Postal address belonging to the supplier/vendor. Do not return the customer's address. |
| `customer_address` | address/string | Postal address belonging to the customer/buyer, bill-to party, or recipient. Do not return the supplier's address. |
| `subtotal` | money/number | Sum before document-level discounts and taxes. |
| `discount` | money/number | Document-level discount amount, not a percentage. |
| `cgst` | money/number | Total Central GST amount. |
| `sgst` | money/number | Total State GST amount. |
| `igst` | money/number | Total Integrated GST amount. |
| `taxable_amount` | money/number | Document-level amount on which tax is calculated, after applicable discounts and before tax. |
| `round_off` | money/number | Signed rounding adjustment. Preserve a negative sign. |
| `grand_total` | money/number | Final payable total after discounts, taxes, and rounding. |
| `currency_code` | currency/string | ISO 4217 code such as `INR`, when it can be determined from the document. (`currency` is reserved by Document AI.) |
| `line_items` | repeated parent | One occurrence for every product/service row. Configure the child fields below. |

## `line_items` child fields

| Field | Type | Description |
|---|---|---|
| `product_name` | string | Product or service name as printed in the row. |
| `description` | string | Additional row description. It may equal `product_name` when the document has only one description column. |
| `hsn_sac` | string | HSN or SAC classification code. Preserve leading zeros. |
| `batch_number` | string | Batch or lot identifier, when present. |
| `expiry_date` | string/date | Expiry value, when present. Preserve partial month-year values such as `12-2035`. |
| `quantity` | number/string | Paid quantity. For `37+7`, return `37`; `free_quantity` may hold `7`. |
| `free_quantity` | number | Free or bonus quantity when separately shown or encoded as `paid+free`. |
| `unit` | string | Unit of measure such as `pcs`, `box`, `kg`, or `roll`. |
| `unit_rate` | money/number | Price per paid unit before row-level discount and tax. |
| `discount_item` | money/number | Row-level discount amount. The suffix keeps this child label distinct from the document-level `discount` label. |
| `discount_percentage` | number | Row-level discount percentage when the table supplies a percentage rather than an amount. |
| `gst_percentage` | number | Combined GST percentage for the row. |
| `taxable_amount_item` | money/number | Row amount after discount and before GST. The suffix keeps this child label distinct from the document-level `taxable_amount` label. |
| `line_total` | money/number | Row total according to the document. Do not substitute the invoice grand total. |

The current response model can also retain `serial_number`, `pack`, `mrp`, and separate CGST/SGST/IGST percentages and amounts. Add those optional children if they matter to the final product.

## Evaluation guidance

- Keep development/training documents separate from the evaluation set.
- Include portrait, landscape, scanned, multilingual, and multi-page documents.
- Evaluate supplier/customer role assignment separately from raw OCR accuracy.
- Treat exact identifiers, dates, GSTINs, amounts, and line-item row boundaries as separate metrics.
- Do not enable automatic acceptance until field-level confidence and Java validation have been calibrated on unseen documents.
