package shortly.mandmcorp.dev.shortly.utils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class DriverIDFormatter {

   public static String formatRiderId(String riderId) {

    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yy");
    String date = LocalDate.now().format(formatter);
    return riderId + "-" + date;
    } 
}
