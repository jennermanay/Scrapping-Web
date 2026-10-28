package pe.edu.monitoreo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import java.nio.file.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class ReportsTest {
    @TempDir Path dir;
    @Test void excelPreservesStringsAndRejectsFormulaInterpretation()throws Exception {
        var path=dir.resolve("reporte.xlsx");var data=new ReportData("Comparaciones",List.of("Producto","Código","URL"),List.of(List.of("=HYPERLINK(\"https://ejemplo.pe\")","00123","https://ejemplo.pe/p")));
        Reports.xlsx(data,path);
        try(var workbook=new org.apache.poi.xssf.usermodel.XSSFWorkbook(Files.newInputStream(path))){var sheet=workbook.getSheetAt(0);assertEquals("Producto",sheet.getRow(0).getCell(0).getStringCellValue());assertEquals(org.apache.poi.ss.usermodel.CellType.STRING,sheet.getRow(1).getCell(0).getCellType());assertEquals("00123",sheet.getRow(1).getCell(1).getStringCellValue());assertEquals(data.rows().getFirst().getFirst(),sheet.getRow(1).getCell(0).getStringCellValue());assertNotNull(sheet.getPaneInformation());}
    }
    @Test void csvEscapesAndNeutralizesFormulas()throws Exception {var data=new ReportData("Prueba",List.of("Nombre","URL"),List.of(List.of("=1+1","https://ejemplo.pe/\"producto\"")));var path=dir.resolve("reporte.csv");Reports.csv(data,path);var text=Files.readString(path);assertTrue(text.contains("\"'=1+1\""));assertTrue(text.contains("\"\"producto\"\""));}
    @Test void pdfPreservesProvenanceAndMultiplePages()throws Exception {var rows=new java.util.ArrayList<List<String>>();for(int i=0;i<30;i++)rows.add(List.of("Leche GLORIA 946ml","https://ejemplo.pe/producto-"+i,"2026-10-21T10:00:00-05:00"));var path=dir.resolve("reporte.pdf");Reports.pdf(new ReportData("Comparaciones",List.of("Producto","URL","Fecha"),rows),path);try(var pdf=Loader.loadPDF(path.toFile())){assertTrue(pdf.getNumberOfPages()>1);var text=new PDFTextStripper().getText(pdf);assertTrue(text.contains("https://ejemplo.pe/producto-29"));assertTrue(text.contains("GLORIA"));}}
}
