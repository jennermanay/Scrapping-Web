package pe.edu.monitoreo;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.time.*;
import java.util.*;
import java.io.StringWriter;
import org.apache.commons.csv.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;

public final class Reports {
    private Reports() {}
    public static void csv(ReportData report,Path path)throws IOException {
        try(var writer=Files.newBufferedWriter(path,StandardCharsets.UTF_8)){writer.write('\ufeff');writer.write(csvLine(report.columns()));writer.newLine();for(var row:report.rows()){writer.write(csvLine(row));writer.newLine();}}
    }
    /** Quotes all fields and neutralizes spreadsheet formula prefixes. */
    static String csvLine(List<String> values){
        var buffer=new StringWriter();
        try(var printer=new CSVPrinter(buffer,CSVFormat.DEFAULT.builder().setQuoteMode(QuoteMode.ALL).setRecordSeparator("").get())){printer.printRecord(values.stream().map(Reports::safeCsv).toList());}
        catch(IOException e){throw new java.io.UncheckedIOException(e);}return buffer.toString();
    }
    private static String safeCsv(String value){return value.matches("^[\\s]*[=+@-].*")?"'"+value:value;}
    /** Exports a workbook using string cells: scraped values are never interpreted as formulas. */
    public static void xlsx(ReportData report,Path path)throws IOException {
        if(report.columns().size()>16384||report.rows().size()>1048575)throw new IllegalArgumentException("El reporte excede la capacidad de una hoja Excel.");
        try(var workbook=new XSSFWorkbook()){
            var sheet=workbook.createSheet("Reporte");var font=workbook.createFont();font.setBold(true);font.setColor(IndexedColors.WHITE.getIndex());
            var header=workbook.createCellStyle();header.setFont(font);header.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            var row=sheet.createRow(0);for(int c=0;c<report.columns().size();c++){var cell=row.createCell(c,CellType.STRING);cell.setCellValue(report.columns().get(c));cell.setCellStyle(header);sheet.setColumnWidth(c,Math.min(70,Math.max(18,report.columns().get(c).length()+3))*256);}
            for(int r=0;r<report.rows().size();r++){var target=sheet.createRow(r+1);for(int c=0;c<report.columns().size();c++){String value=report.rows().get(r).get(c);if(value.length()>32767)throw new IllegalArgumentException("Una celda excede el límite de texto Excel.");target.createCell(c,CellType.STRING).setCellValue(value);}}
            sheet.createFreezePane(0,1);if(!report.columns().isEmpty())sheet.setAutoFilter(new CellRangeAddress(0,report.rows().size(),0,report.columns().size()-1));workbook.getProperties().getCoreProperties().setTitle(report.title());
            try(var out=Files.newOutputStream(path)){workbook.write(out);}
        }
    }
    public static void pdf(ReportData report,Path path)throws IOException {
        try(var doc=new PDDocument()) {
            var font=new PDType1Font(Standard14Fonts.FontName.HELVETICA);var bold=new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            var lines=new ArrayList<String>();lines.add(report.title());lines.add("Generado: "+ZonedDateTime.now(ZoneId.of("America/Lima")).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")));lines.add("Registros: "+report.rows().size());lines.add("");
            int count=0;for(var row:report.rows()){lines.add("Registro "+(++count));for(int i=0;i<report.columns().size();i++)lines.addAll(wrap(report.columns().get(i)+": "+row.get(i),font,510));lines.add("");}
            for(int start=0;start<lines.size();start+=49){var page=new PDPage(PDRectangle.A4);doc.addPage(page);try(var stream=new PDPageContentStream(doc,page)){
                stream.beginText();stream.setFont(font,10);stream.setLeading(15);stream.newLineAtOffset(40,790);
                for(int i=start;i<Math.min(start+49,lines.size());i++){stream.setFont(i==0?bold:font,i==0?14:10);stream.showText(safe(lines.get(i)));stream.newLine();}stream.endText();
                stream.beginText();stream.setFont(font,9);stream.newLineAtOffset(40,25);stream.showText("Monitoreo de precios - Pagina "+doc.getNumberOfPages());stream.endText();
            }}doc.save(path.toFile());
        }
    }
    private static String safe(String s){return s.replaceAll("[^\\x20-\\x7E\\xA0-\\xFF]","?");}
    private static List<String> wrap(String text,PDFont font,float width)throws IOException {
        var result=new ArrayList<String>();text=safe(text.replaceAll("[\\r\\n\\t]"," "));
        while(font.getStringWidth(text)/1000*10>width){int lo=1,hi=text.length();while(lo<hi){int mid=(lo+hi+1)/2;if(font.getStringWidth(text.substring(0,mid))/1000*10<=width)lo=mid;else hi=mid-1;}int pos=text.lastIndexOf(' ',lo);if(pos<lo/2)pos=lo;result.add(text.substring(0,pos));text=text.substring(pos).stripLeading();}
        result.add(text);return result;
    }
}
