package pe.edu.monitoreo;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.time.*;
import java.util.*;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;

public final class Reports {
    private Reports() {}
    public static void csv(ReportData report,Path path)throws IOException {
        try(var writer=Files.newBufferedWriter(path,StandardCharsets.UTF_8)){writer.write('\ufeff');writer.write(csvLine(report.columns()));writer.newLine();for(var row:report.rows()){writer.write(csvLine(row));writer.newLine();}}
    }
    static String csvLine(List<String> values){return values.stream().map(s->{if(s.matches("^[\\s]*[=+@-].*"))s="'"+s;return "\""+s.replace("\"","\"\"")+"\"";}).collect(java.util.stream.Collectors.joining(","));}
    public static void pdf(ReportData report,Path path)throws IOException {
        try(var doc=new PDDocument()) {
            var font=new PDType1Font(Standard14Fonts.FontName.HELVETICA);var bold=new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            var lines=new ArrayList<String>();lines.add(report.title());lines.add("Generado: "+ZonedDateTime.now(ZoneId.of("America/Lima")));lines.add("Registros: "+report.rows().size());lines.add("");
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
