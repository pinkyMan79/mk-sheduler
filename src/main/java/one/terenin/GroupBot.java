package one.terenin;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetRequest;
import com.google.api.services.sheets.v4.model.DeleteDimensionRequest;
import com.google.api.services.sheets.v4.model.DimensionRange;
import com.google.api.services.sheets.v4.model.Request;
import com.google.api.services.sheets.v4.model.ValueRange;
import lombok.SneakyThrows;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.io.FileInputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GroupBot extends TelegramLongPollingBot {
    private static final String SPREADSHEET_ID = "1mbORWzUo8omx6VW1quiF5SUg4prEvsCF2U3EaMqnKvw";
    private static Sheets sheetsService;

    public GroupBot() throws IOException {
        sheetsService = getSheetsService();
        System.out.println("Init completed sheet service");
    }

    @Override
    public String getBotUsername() {
        return "MKSheduler";
    }

    @Override
    public String getBotToken() {
        // мне не жалко если ты узнаешь креды бота, в любом случае, кредов для таблички у тебя нет, так что брось эту затею
        return "7550025393:AAFCLxYu2OJQWpJE-mcNw7QrSYUz9dcS47c";
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String messageText = update.getMessage().getText();
            System.out.println(messageText);
            String username = update.getMessage().getFrom().getUserName();
            if (username == null) username = update.getMessage().getFrom().getFirstName();

            if (messageText.matches("Занимаю мк с \\d{2}:\\d{2} до \\d{2}:\\d{2} \\d{2}.\\d{2}.\\d{2}")) {
                try {
                    System.out.println("Catch message: " + messageText);
                    String[] parts = messageText.split(" ");
                    String startTime = parts[3];
                    String endTime = parts[5];
                    String date = parts[6];

                    /*DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yy");

                    LocalDate ldDate = LocalDate.parse(date, formatter);
                    if (LocalDate.now().getYear() < ldDate.getYear() || LocalDate.now().getMonthValue() < ldDate.getMonthValue() || LocalDate.now().getDayOfMonth() < ldDate.getDayOfMonth()) {
                        throw new RuntimeException("Прошлое в прошлом, просто прими это и попытайся снова, ты сможешь. Проверь дату");
                    }*/

                    String range = "Sheet1!A:E";
                    ValueRange data = sheetsService.spreadsheets().values()
                            .get(SPREADSHEET_ID, range)
                            .execute();
                    data.getValues().forEach(it -> {
                        if (date.equals(it.get(4))) { // if new date overlaps with existing we need to check possible time overlaps
                            String startTimeCurr = (String) it.get(2);
                            String endTimeCurr = (String) it.get(3);
                            System.out.println(startTimeCurr + " " + endTimeCurr);
                            boolean ovRes = checkTimeOverlaps(startTime, endTime, startTimeCurr, endTimeCurr);
                            if (ovRes) {
                                throw new RuntimeException(String.format("Время перескается с %s, точка забита с %s до %s", it.get(1), startTimeCurr, endTimeCurr));
                            }
                        }
                    });

                    writeToSheet(username, startTime, endTime, date);
                    sendReply(update.getMessage().getChatId(), String.format("Пересечений не найдено. Ты мк за тобой с %s до %s на дату %s", startTime, endTime, date));
                    System.out.println(update.getMessage().getChatId() + " " + "Запись успешно добавлена!");
                } catch (Exception e) {
                    sendReply(update.getMessage().getChatId(), "Ошибка при добавлении записи." + e.getMessage());
                    e.printStackTrace();
                }
            } else if (messageText.startsWith("Отменяю запись мк с")) {
                System.out.println("Catch message: " + messageText);
                String[] parts = messageText.split(" ");
                String startTime = parts[4];
                String endTime = parts[6];
                String date = parts[7];

                // Читаем данные из таблицы
                String range = "Sheet1"; // Укажите лист, где хранятся записи
                ValueRange response = null;
                try {
                    response = sheetsService.spreadsheets().values()
                            .get(SPREADSHEET_ID, range)
                            .execute();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

                List<List<Object>> rows = response.getValues();

                if (rows == null || rows.isEmpty()) {
                    System.out.println("Таблица пуста.");
                    sendReply(update.getMessage().getChatId(), "В таблице записей нет");
                    return;
                }

                int rowIndexToDelete = -1;
                for (int i = 0; i < rows.size(); i++) {
                    List<Object> row = rows.get(i);
                    if (row.size() >= 4) {
                        String rowUsername = row.get(1).toString();
                        String rowStartTime = row.get(2).toString();
                        String rowEndTime = row.get(3).toString();
                        String rowDate = row.get(4).toString();

                        if (rowDate.equals(date)
                                && rowStartTime.equals(startTime)
                                && rowUsername.equals(username)
                                && rowEndTime.equals(endTime)) {
                            rowIndexToDelete = i;
                            break;
                        }
                    }
                }

                if (rowIndexToDelete != -1) {
                    try {
                        deleteRow(rowIndexToDelete + 1); // for accept indexes in google-sheet-like style
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    System.out.println("Запись удалена.");
                    sendReply(update.getMessage().getChatId(), "Запись удалена из таблицы, теперь балдей, возьми пивка и зачилься на диване");
                } else {
                    System.out.println("Запись не найдена.");
                    sendReply(update.getMessage().getChatId(), "Такой записи нет");
                }
            }
        }
    }

    private boolean checkTimeOverlaps(String startTime, String endTime, String startTimeFromRemote, String endTimeFromRemote) {
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");
        LocalTime start = LocalTime.parse(startTime, timeFormatter);
        LocalTime end = LocalTime.parse(endTime, timeFormatter);
        LocalTime remoteStart = LocalTime.parse(startTimeFromRemote, timeFormatter);
        LocalTime remoteEnd = LocalTime.parse(endTimeFromRemote, timeFormatter);
        return (start.isBefore(remoteEnd) && end.isAfter(remoteStart));
    }

    private void sendReply(Long chatId, String text) {
        try {
            var message = new org.telegram.telegrambots.meta.api.methods.send.SendMessage();
            message.setChatId(chatId);
            message.setText(text);
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void writeToSheet(String username, String startTime, String endTime, String date) throws IOException {
        List<List<Object>> values = new ArrayList<>();
        values.add(List.of(LocalDateTime.now().toString(), username, startTime, endTime, date)); // Дата, пользователь, время начала, время окончания

        ValueRange body = new ValueRange().setValues(values);
        sheetsService.spreadsheets().values()
                .append(SPREADSHEET_ID, "Sheet1!A1:D1", body)
                .setValueInputOption("RAW")
                .execute();
    }

/*    private void removeFromSheet(String username, String startTime, String endTime, String date) throws IOException {
        List<List<Object>> values = new ArrayList<>();
        // not posiible to use contains, check jdk collection api for more info
        int index = 0;
        for (List<Object> value : values) {
            String val = value.get(1).toString() + " " + value.get(2).toString() + " " + value.get(3).toString() + " " + value.get(4).toString();
            index++;
            if (val.equals(username + " " + startTime + " " + endTime + " " + date)) {
                System.out.println("Found line for removal");
                values.remove(index);
                break;
            }
        }
        BatchClearValuesRequest batchClearValuesRequest = new BatchClearValuesRequest();
        batchClearValuesRequest.set()
        sheetsService.spreadsheets().values()
                .batchClear(SPREADSHEET_ID, )
                .execute();
    }*/

    private void deleteRow(int rowIndex) throws IOException {
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

    @SneakyThrows
    private static Sheets getSheetsService() throws IOException {
        System.out.println("Init sheet service");
        FileInputStream serviceAccountStream = new FileInputStream("/home/lino/Downloads/tshed/src/main/resources/mksheduler-3704f00dba70.json");
        GoogleCredential credentials = GoogleCredential.fromStream(serviceAccountStream)
                .createScoped(Collections.singleton(SheetsScopes.SPREADSHEETS));
        System.out.println("Find data for sheet service");
        return new Sheets.Builder(new com.google.api.client.http.javanet.NetHttpTransport(), new GsonFactory(), credentials)
                .setApplicationName("Расписание реп в МК")
                .build();
    }

    public static void main(String[] args) {
        try {
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botsApi.registerBot(new GroupBot());
            System.out.println("Initialized");
        } catch (TelegramApiException | IOException e) {
            e.printStackTrace();
        }
    }
}
