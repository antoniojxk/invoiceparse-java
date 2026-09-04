import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Generates synthetic, non-commercial demonstration documents without third-party dependencies. */
public class SampleInvoiceGenerator {
    private static final Path OUTPUT = Path.of("samples");

    public static void main(String[] args) throws Exception {
        Files.createDirectories(OUTPUT.resolve("expected"));
        createDigitalPdf();
        createImageInvoice();
        System.out.println("Generated samples in " + OUTPUT.toAbsolutePath());
    }

    private static void createDigitalPdf() throws IOException {
        List<String> lines = List.of(
                "TAX INVOICE - SYNTHETIC DEMO", "Supplier: Example Components Pvt Ltd",
                "Supplier GSTIN: 27ABCDE1234F1Z5", "Address: 10 Demo Park, Pune, Maharashtra",
                "Customer: Sample Retail LLP", "Customer GSTIN: 29PQRSX5678K1Z2",
                "Invoice No: SI-2026-104", "Invoice Date: 07-Aug-2026",
                "Description | HSN/SAC | Qty | Unit | Rate | GST % | Taxable Amount | Total",
                "Copper Cable | 8544 | 2 | roll | 500.00 | 18 | 1000.00 | 1000.00",
                "USB Adapter | 8504 | 3 | pcs | 200.00 | 18 | 600.00 | 600.00",
                "Taxable Amount: 1600.00", "CGST: 144.00", "SGST: 144.00", "Grand Total: INR 1888.00");
        Files.write(OUTPUT.resolve("digital-invoice-layout-a.pdf"), simplePdf(lines));
    }

    private static byte[] simplePdf(List<String> lines) throws IOException {
        StringBuilder content = new StringBuilder();
        content.append("0.11 0.24 0.38 rg 0 742 595 100 re f\n");
        content.append("BT /F2 22 Tf 1 1 1 rg 46 792 Td (").append(escapePdf(lines.getFirst())).append(") Tj ET\n");
        content.append("BT /F2 11 Tf 0.82 0.90 0.98 rg 46 766 Td (Selectable text PDF / synthetic data only) Tj ET\n");
        content.append("0.94 0.96 0.98 rg 40 581 515 24 re f\n");
        content.append("0.11 0.24 0.38 RG 0.7 w 40 580 m 555 580 l S\n");
        content.append("BT /F1 10 Tf 0.08 0.10 0.12 rg 46 714 Td 18 TL\n");
        for (String line : lines.subList(1, lines.size())) content.append('(').append(escapePdf(line)).append(") Tj T*\n");
        content.append("ET\n");
        content.append("BT /F1 8 Tf 0.35 0.38 0.42 rg 46 46 Td (Generated for InvoiceParse Java - no real commercial data) Tj ET\n");
        byte[] stream = content.toString().getBytes(StandardCharsets.ISO_8859_1);
        List<String> objects = List.of(
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 4 0 R /F2 5 0 R >> >> /Contents 6 0 R >>",
                "<< /Type /Font /Subtype /Type1 /BaseFont /Courier >>",
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold >>",
                "<< /Length " + stream.length + " >>\nstream\n" + new String(stream, StandardCharsets.ISO_8859_1) + "endstream");
        var out = new ByteArrayOutputStream();
        out.write("%PDF-1.4\n%synthetic\n".getBytes(StandardCharsets.ISO_8859_1));
        var offsets = new ArrayList<Integer>(); offsets.add(0);
        for (int i = 0; i < objects.size(); i++) {
            offsets.add(out.size());
            out.write(((i + 1) + " 0 obj\n" + objects.get(i) + "\nendobj\n").getBytes(StandardCharsets.ISO_8859_1));
        }
        int xref = out.size();
        out.write(("xref\n0 " + (objects.size() + 1) + "\n0000000000 65535 f \n").getBytes(StandardCharsets.ISO_8859_1));
        for (int i = 1; i < offsets.size(); i++) out.write(String.format("%010d 00000 n \n", offsets.get(i)).getBytes(StandardCharsets.ISO_8859_1));
        out.write(("trailer << /Size " + (objects.size() + 1) + " /Root 1 0 R >>\nstartxref\n" + xref + "\n%%EOF\n")
                .getBytes(StandardCharsets.ISO_8859_1));
        return out.toByteArray();
    }

    private static String escapePdf(String value) { return value.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)"); }

    private static void createImageInvoice() throws IOException {
        var image = new BufferedImage(1600, 1200, BufferedImage.TYPE_INT_RGB);
        var g = image.createGraphics();
        g.setColor(Color.WHITE); g.fillRect(0, 0, image.getWidth(), image.getHeight());
        g.setColor(new Color(27, 55, 84)); g.fillRect(0, 0, image.getWidth(), 135);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(Color.WHITE); g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 42)); g.drawString("NORTHWIND WHOLESALE", 70, 62);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 30)); g.drawString("GST INVOICE", 70, 108);
        g.setColor(Color.BLACK); g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 25));
        String[] details = {"Supplier GSTIN: 19ABCDE1234F1Z7", "Customer: Sample Office Supplies",
                "Customer GSTIN: 29PQRSX5678K1Z2", "Bill No: PI-7781", "Date: 18/08/2026"};
        int y = 190; for (String line : details) { g.drawString(line, 70, y); y += 40; }
        y += 25; g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 23));
        g.drawString("Product Description     Quantity    Rate       Amount", 70, y);
        g.drawLine(65, y + 12, 1510, y + 12); y += 58;
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 23));
        g.drawString("Paper Ream A4           5           240.00     1200.00", 70, y); y += 54;
        g.drawString("Marker Pack             2           150.00      300.00", 70, y); y += 54;
        g.drawString("Desk Organizer          1           450.00      450.00", 70, y); y += 45;
        g.drawLine(65, y, 1510, y); y += 55;
        g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 25));
        g.drawString("Subtotal: 1950.00", 950, y); y += 43;
        g.drawString("IGST: 351.00", 950, y); y += 43;
        g.drawString("Grand Total: INR 2301.00", 950, y);
        g.setFont(new Font(Font.SANS_SERIF, Font.ITALIC, 18)); g.drawString("Synthetic demo - no real business or personal data", 70, 1120);
        g.dispose();
        ImageIO.write(image, "png", OUTPUT.resolve("image-invoice-layout-b.png").toFile());
    }
}
