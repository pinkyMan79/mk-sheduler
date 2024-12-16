package one.terenin;

import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.ValueRange;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.groupadministration.GetChatMember;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.chatmember.ChatMember;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.io.IOException;
import java.util.List;

import static one.terenin.SheetService.*;
import static one.terenin.TimeUtil.checkTimeOverlaps;

public class GroupBot extends TelegramLongPollingBot {
    private static final String SPREADSHEET_ID = "1mbORWzUo8omx6VW1quiF5SUg4prEvsCF2U3EaMqnKvw";
    private static Sheets sheetsService;
    private static final long ALLOWED_CHAT_ID = -1001874565795L;

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

    private boolean isUserAllowed(Long userId) {
        try {
            GetChatMember getChatMember = new GetChatMember(String.valueOf(ALLOWED_CHAT_ID), userId);
            ChatMember member = execute(getChatMember);
            String status = member.getStatus();
            return "member".equals(status) || "administrator".equals(status) || "creator".equals(status);
        } catch (TelegramApiException e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {

            User user = update.getMessage().getFrom();
            Long chatId = update.getMessage().getChatId();

            if (isUserAllowed(user.getId())) {
                // Обработка запроса для авторизованных пользователей
                try {
                    String messageText = update.getMessage().getText();
                    System.out.println(messageText);
                    String username = update.getMessage().getFrom().getUserName();
                    if (username == null) username = update.getMessage().getFrom().getFirstName();

                    if (messageText.matches("Занимаю мк с \\d{2}:\\d{2} до \\d{2}:\\d{2} \\d{2}.\\d{2}.\\d{2}")) {
                        acceptRecord(update, messageText, username);
                    } else if (messageText.startsWith("Отменяю запись мк с")) {
                        revokeRecord(update, messageText, username);
                    }
                    execute(SendMessage.builder()
                            .chatId(chatId.toString())
                            .text("Ваш запрос обработан")
                            .build());
                } catch (TelegramApiException e) {
                    e.printStackTrace();
                }
            } else {
                // Ответ для неавторизованных пользователей
                try {
                    execute(SendMessage.builder()
                            .chatId(chatId.toString())
                            .text("Извините, доступ запрещён. Вы не музыкантик.")
                            .build());
                } catch (TelegramApiException e) {
                    e.printStackTrace();
                }
            }
        }
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

    public static void main(String[] args) {
        try {
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botsApi.registerBot(new GroupBot());
            System.out.println("Initialized");
        } catch (TelegramApiException | IOException e) {
            e.printStackTrace();
        }
    }

    private void acceptRecord(Update update, String messageText, String username) {
        try {
            System.out.println("Catch message: " + messageText);
            String[] parts = messageText.split(" ");
            String startTime = parts[3];
            String endTime = parts[5];
            String date = parts[6];

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

            writeToSheet(username, startTime, endTime, date, sheetsService);
            sendReply(update.getMessage().getChatId(), String.format("Пересечений не найдено. Ты мк за тобой с %s до %s на дату %s", startTime, endTime, date));
            System.out.println(update.getMessage().getChatId() + " " + "Запись успешно добавлена!");
        } catch (Exception e) {
            sendReply(update.getMessage().getChatId(), "Ошибка при добавлении записи." + e.getMessage());
            e.printStackTrace();
        }
    }

    private void revokeRecord(Update update, String messageText, String username) {
        System.out.println("Catch message: " + messageText);
        String[] parts = messageText.split(" ");
        String startTime = parts[4];
        String endTime = parts[6];
        String date = parts[7];

        String range = "Sheet1";
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
                deleteRow(rowIndexToDelete + 1, sheetsService); // for accept indexes in google-sheet-like style
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
