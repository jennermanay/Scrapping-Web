package pe.edu.monitoreo;
import java.time.LocalDate;
public record ReportFilter(long product,long source,LocalDate from,LocalDate to) { public static ReportFilter all(){return new ReportFilter(0,0,null,null);} }
