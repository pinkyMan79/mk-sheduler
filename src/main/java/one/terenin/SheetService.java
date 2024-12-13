package one.terenin;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.*;
import lombok.SneakyThrows;

import java.io.FileInputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


public class SheetService {

    public static final String SPREADSHEET_ID = "1mbORWzUo8omx6VW1quiF5SUg4prEvsCF2U3EaMqnKvw";

    @SneakyThrows
    public static Sheets getSheetsService() throws IOException {
        System.out.println("Init sheet service");
        FileInputStream serviceAccountStream = new FileInputStream("/home/lino/Downloads/tshed/src/main/resources/mksheduler-3704f00dba70.json");
        GoogleCredential credentials = GoogleCredential.fromStream(serviceAccountStream)
                .createScoped(Collections.singleton(SheetsScopes.SPREADSHEETS));
        System.out.println("Find data for sheet service");
        return new Sheets.Builder(new com.google.api.client.http.javanet.NetHttpTransport(), new GsonFactory(), credentials)
                .setApplicationName("Расписание реп в МК")
                .build();
    }

    public static void deleteRow(int rowIndex, Sheets sheetsService) throws IOException {
        List<Request> requests = new ArrayList<>();
        requests.add(new Request().setDeleteDimension(new DeleteDimensionRequest()
                .setRange(new DimensionRange()
                        .setSheetId(0)
                        .setDimension("ROWS")
                        .setStartIndex(rowIndex - 1)
                        .setEndIndex(rowIndex))));

        BatchUpdateSpreadsheetRequest batchUpdateRequest = new BatchUpdateSpreadsheetRequest()
                .setRequests(requests);

        sheetsService.spreadsheets()
                .batchUpdate(SPREADSHEET_ID, batchUpdateRequest)
                .execute();
    }

    public static void writeToSheet(String username, String startTime, String endTime, String date, Sheets sheetsService) throws IOException {
        List<List<Object>> values = new ArrayList<>();
        values.add(List.of(LocalDateTime.now().toString(), username, startTime, endTime, date)); // Дата, пользователь, время начала, время окончания

        ValueRange body = new ValueRange().setValues(values);
        sheetsService.spreadsheets().values()
                .append(SPREADSHEET_ID, "Sheet1!A1:D1", body)
                .setValueInputOption("RAW")
                .execute();
    }
}
