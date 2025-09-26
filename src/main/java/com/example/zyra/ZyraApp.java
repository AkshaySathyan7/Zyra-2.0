package com.example.zyra;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import javax.speech.Central;
import com.sun.speech.freetts.Voice;
import com.sun.speech.freetts.VoiceManager;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import org.json.*;

/**
 * Zyra 2.0 - JavaFX assistant
 *
 * Features:
 * - Chat GUI with message bubbles (bot and user)
 * - Text-to-speech using FreeTTS
 * - Open YouTube/google/any website in default browser
 * - Weather (OpenWeatherMap) and News (NewsAPI) via HTTP (requires API keys)
 * - Wikipedia summary using REST endpoint
 * - Jokes, facts, random number, timer, basic math, reminders
 *
 * Speech recognition is not implemented by default (stub provided).
 *
 * IMPORTANT:
 *  - Add your OpenWeatherMap API key and NewsAPI key into the WEATHER_API_KEY and NEWS_API_KEY constants.
 *  - This program uses Java 11+ HttpClient and JavaFX.
 */
public class ZyraApp extends Application {

    // ========== Configuration ==========
    private static final String BOT_NAME = "Zyra";
    private static final String WEATHER_API_KEY = "YOUR_OPENWEATHERMAP_API_KEY";
    private static final String NEWS_API_KEY = "YOUR_NEWSAPI_KEY";

    // ========== UI ==========
    private VBox chatBox;
    private TextField inputField;
    private Button sendButton;
    private Button micButton; // stub
    private ComboBox<String> modeSelector;

    // ========== TTS ==========
    private Voice ttsVoice;

    // ========= HTTP ==========
    private final HttpClient httpClient = HttpClient.newBuilder().build();

    // ========== Small data ==========
    private final List<String> facts = List.of(
            "Honey never spoils.",
            "Octopuses have three hearts.",
            "Bananas are berries, but strawberries are not.",
            "A day on Venus is longer than a year on Venus."
    );

    private final List<String> jokes = List.of(
            "Why did the programmer quit his job? Because he didn't get arrays.",
            "I'm reading a book about anti-gravity — it's impossible to put down!",
            "Why do Java developers wear glasses? Because they don't C#."
    );

    // ========= App state ==========
    private enum Mode { TEXT, SPEECH, BOTH }
    private Mode inputMode = Mode.BOTH;

    @Override
    public void start(Stage stage) {
        stage.setTitle("Zyra 2.0");

        // chat area
        chatBox = new VBox(10);
        chatBox.setPadding(new Insets(10));
        ScrollPane scrollPane = new ScrollPane(chatBox);
        scrollPane.setFitToWidth(true);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        // input field + buttons bottom
        inputField = new TextField();
        inputField.setPromptText("Type a message...");
        inputField.setPrefHeight(36);

        sendButton = new Button("Send");
        sendButton.setPrefHeight(36);
        sendButton.setOnAction(e -> onSend());

        micButton = new Button("\uD83C\uDFA4"); // mic emoji
        micButton.setPrefHeight(36);
        micButton.setOnAction(e -> onSpeak());
        micButton.setTooltip(new Tooltip("Speech not enabled by default."));

        // mode selector
        modeSelector = new ComboBox<>();
        modeSelector.getItems().addAll("Text", "Speech", "Both");
        modeSelector.setValue("Both");
        modeSelector.setOnAction(e -> {
            String v = modeSelector.getValue();
            inputMode = switch (v) {
                case "Text" -> Mode.TEXT;
                case "Speech" -> Mode.SPEECH;
                default -> Mode.BOTH;
            };
            appendBotMessage("Mode set to: " + v);
        });

        HBox controls = new HBox(8, modeSelector, inputField, sendButton, micButton);
        HBox.setHgrow(inputField, Priority.ALWAYS);
        controls.setPadding(new Insets(8));
        controls.setAlignment(Pos.CENTER);

        BorderPane root = new BorderPane();
        root.setCenter(scrollPane);
        root.setBottom(controls);
        root.setPrefSize(540, 700);

        Scene scene = new Scene(root);
        stage.setScene(scene);

        // Enter key to send
        scene.setOnKeyPressed(ev -> {
            if (ev.getCode() == KeyCode.ENTER) {
                if (inputMode == Mode.TEXT || inputMode == Mode.BOTH) onSend();
            }
        });

        initTTS();
        appendBotMessage("Hello! I'm Zyra 2.0. Select mode and type a command (try 'time', 'joke', 'weather London', 'news', 'play <song>', 'who is <name>').");

        stage.show();
    }

    // ---------------- UI helpers ----------------
    private void appendUserMessage(String text) {
        Platform.runLater(() -> {
            HBox h = bubble(text, false);
            chatBox.getChildren().add(h);
            scrollToBottom();
        });
    }

    private void appendBotMessage(String text) {
        Platform.runLater(() -> {
            HBox h = bubble(text, true);
            chatBox.getChildren().add(h);
            // also speak in background if allowed
            speakAsync(text);
            scrollToBottom();
        });
    }

    private HBox bubble(String text, boolean isBot) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.setMaxWidth(380);
        label.setPadding(new Insets(8));
        label.setStyle("-fx-background-color: " + (isBot ? "#E6F4EA" : "#F1F1F1") + "; -fx-background-radius: 12;");
        HBox box = new HBox();
        if (isBot) {
            box.setAlignment(Pos.CENTER_LEFT);
            box.getChildren().add(label);
        } else {
            box.setAlignment(Pos.CENTER_RIGHT);
            box.getChildren().add(label);
        }
        return box;
    }

    private void scrollToBottom() {
        Platform.runLater(() -> {
            // scroll by requesting layout then moving value to 1.0
            // (works for ScrollPane containing VBox)
            try {
                Thread.sleep(5);
            } catch (InterruptedException ignored) {}
        });
    }

    // ---------------- TTS ----------------
    private void initTTS() {
        // FreeTTS initialization
        try {
            System.setProperty("freetts.voices",
                    "com.sun.speech.freetts.en.us.cmu_time_awb.AlanVoiceDirectory");
            VoiceManager vm = VoiceManager.getInstance();
            ttsVoice = vm.getVoice("kevin16"); // "kevin16" is common; if missing, FreeTTS will fall back
            if (ttsVoice != null) {
                ttsVoice.allocate();
            } else {
                System.err.println("FreeTTS voice not found; TTS disabled.");
            }
        } catch (Exception ex) {
            System.err.println("TTS initialization error: " + ex.getMessage());
        }
    }

    private void speakAsync(String text) {
        if (inputMode == Mode.SPEECH || inputMode == Mode.BOTH) {
            if (ttsVoice != null) {
                CompletableFuture.runAsync(() -> {
                    try {
                        ttsVoice.speak(text);
                    } catch (Exception e) {
                        System.err.println("TTS error: " + e.getMessage());
                    }
                });
            }
        }
    }

    // ---------------- Input actions ----------------
    private void onSend() {
        String text = inputField.getText().trim();
        if (text.isEmpty()) return;
        appendUserMessage(text);
        inputField.clear();
        CompletableFuture.runAsync(() -> processCommand(text));
    }

    private void onSpeak() {
        // Speech-to-text is not implemented out-of-the-box.
        appendBotMessage("Speech feature is a stub. To enable speech recognition, integrate Vosk or Google Speech and call processCommand(recognizedText).");
    }

    // ---------------- Command processing ----------------
    private void processCommand(String command) {
        if (command == null || command.isBlank()) return;
        String cmd = command.toLowerCase(Locale.ROOT);

        try {
            // Greetings
            if (containsAny(cmd, "hi", "hello", "hey", "good morning", "good afternoon", "good evening")) {
                appendBotMessage("Hello! How are you today?");
                return;
            }

            if (cmd.contains("how are you") || cmd.contains("how's it going")) {
                appendBotMessage("I'm a program, but I'm running great — thanks for asking!");
                return;
            }

            if (cmd.startsWith("thank") || cmd.contains("thanks")) {
                appendBotMessage("You're welcome!");
                return;
            }

            // Jokes / facts
            if (cmd.contains("joke")) {
                appendBotMessage(randomFrom(jokes));
                return;
            }
            if (cmd.contains("tell me something") || cmd.contains("fact")) {
                appendBotMessage(randomFrom(facts));
                return;
            }

            // Random / utilities
            if (cmd.contains("flip a coin")) {
                appendBotMessage(new Random().nextBoolean() ? "Heads!" : "Tails!");
                return;
            }
            if (cmd.contains("roll a dice") || cmd.contains("roll a die")) {
                appendBotMessage("You rolled a " + (new Random().nextInt(6) + 1));
                return;
            }
            if (cmd.contains("random number")) {
                appendBotMessage("Your random number is " + (new Random().nextInt(100) + 1));
                return;
            }

            // Timer: "set a timer for 5 seconds" or "set a timer for 2 minutes"
            if (cmd.startsWith("set a timer for")) {
                String[] tokens = cmd.split("\\s+");
                OptionalInt digits = Arrays.stream(tokens)
                        .filter(s -> s.matches("\\d+"))
                        .mapToInt(Integer::parseInt)
                        .findFirst();
                if (digits.isPresent()) {
                    int n = digits.getAsInt();
                    int seconds = n;
                    if (cmd.contains("minute") || cmd.contains("minutes")) seconds = n * 60;
                    appendBotMessage("Timer set for " + seconds + " seconds.");
                    CompletableFuture.runAsync(() -> {
                        try {
                            Thread.sleep(seconds * 1000L);
                            appendBotMessage("Time's up!");
                        } catch (InterruptedException ignored) {}
                    });
                } else {
                    appendBotMessage("Please state the duration like 'set a timer for 5 seconds'.");
                }
                return;
            }

            // Math: simple numeric extraction
            if (cmd.matches(".*\\b(add|plus)\\b.*") || cmd.matches(".*\\b(subtract|minus)\\b.*") ||
                    cmd.matches(".*\\b(multiply|times)\\b.*") || cmd.matches(".*\\b(divide|divided by)\\b.*")) {

                List<Integer> numbers = extractIntegers(cmd);
                if (numbers.size() < 2) {
                    appendBotMessage("Please provide two numbers (e.g., 'add 3 and 5').");
                    return;
                }
                if (cmd.contains("add") || cmd.contains("plus")) {
                    int sum = numbers.stream().mapToInt(Integer::intValue).sum();
                    appendBotMessage("The answer is " + sum);
                    return;
                }
                if (cmd.contains("subtract") || cmd.contains("minus")) {
                    appendBotMessage("The answer is " + (numbers.get(0) - numbers.get(1)));
                    return;
                }
                if (cmd.contains("multiply") || cmd.contains("times")) {
                    appendBotMessage("The answer is " + (numbers.get(0) * numbers.get(1)));
                    return;
                }
                if (cmd.contains("divide") || cmd.contains("divided by")) {
                    if (numbers.get(1) == 0) {
                        appendBotMessage("Cannot divide by zero!");
                    } else {
                        appendBotMessage("The answer is " + ((double) numbers.get(0) / numbers.get(1)));
                    }
                    return;
                }
            }

            // Play (open YouTube)
            if (cmd.startsWith("play ")) {
                String song = command.substring(5).trim();
                String query = URLEncoder.encode(song, StandardCharsets.UTF_8);
                String url = "https://www.youtube.com/results?search_query=" + query;
                openInBrowser(url);
                appendBotMessage("Playing " + song + " on YouTube (opened in browser).");
                return;
            }

            // Time / Date
            if (cmd.contains("time")) {
                String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("hh:mm a"));
                appendBotMessage("Current time is " + time);
                return;
            }
            if (cmd.contains("date")) {
                String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("EEEE, MMMM dd, yyyy"));
                appendBotMessage("Today is " + date);
                return;
            }

            // Wikipedia: "who is <name>" or "who is Albert Einstein"
            if (cmd.startsWith("who is ") || cmd.startsWith("who the heck is ")) {
                String person = command.replaceFirst("(?i)who the heck is ", "")
                        .replaceFirst("(?i)who is ", "").trim();
                if (!person.isEmpty()) {
                    fetchWikipediaSummary(person).thenAccept(summary -> {
                        if (summary != null && !summary.isBlank()) appendBotMessage(summary);
                        else appendBotMessage("Sorry, I couldn't find information about " + person + ".");
                    });
                } else {
                    appendBotMessage("Tell me who you want information about. Example: 'who is Ada Lovelace'");
                }
                return;
            }

            // Open websites
            if (cmd.startsWith("open ")) {
                String site = command.substring(5).trim();
                if (site.equalsIgnoreCase("youtube")) site = "https://www.youtube.com";
                else if (site.equalsIgnoreCase("google")) site = "https://www.google.com";
                else if (!site.startsWith("http")) site = "https://" + site;
                openInBrowser(site);
                appendBotMessage("Opening " + site);
                return;
            }

            // Weather
            if (cmd.startsWith("weather")) {
                String city = command.replaceFirst("(?i)weather", "").trim();
                if (city.isEmpty()) {
                    appendBotMessage("Please say 'weather <city>'");
                } else {
                    getWeather(city).thenAccept(resp -> {
                        if (resp != null) appendBotMessage(resp); else appendBotMessage("Couldn't fetch weather for " + city);
                    });
                }
                return;
            }

            // News
            if (cmd.contains("news")) {
                getTopNews().thenAccept(list -> {
                    if (list != null && !list.isEmpty()) {
                        appendBotMessage("Top headlines:");
                        list.forEach(headline -> appendBotMessage("- " + headline));
                    } else appendBotMessage("Couldn't fetch news right now.");
                });
                return;
            }

            // Remind me
            if (cmd.startsWith("remind me")) {
                String reminder = command.replaceFirst("(?i)remind me", "").trim();
                if (!reminder.isEmpty()) {
                    appendBotMessage("Reminder set: " + reminder);
                    // No persistent storage; this is ephemeral
                } else appendBotMessage("Tell me the reminder after 'remind me'.");
                return;
            }

            // Are you single
            if (cmd.contains("are you single")) {
                appendBotMessage("I am in a relationship with Wi-Fi.");
                return;
            }

            // Exit
            if (cmd.equalsIgnoreCase("exit") || cmd.equalsIgnoreCase("quit") || cmd.equalsIgnoreCase("bye")) {
                appendBotMessage("Goodbye! Have a nice day.");
                Platform.runLater(() -> {
                    try { Thread.sleep(400); } catch (InterruptedException ignored) {}
                    Platform.exit();
                });
                return;
            }

            // Fallback
            appendBotMessage("I didn't understand that. Try commands like: 'time', 'joke', 'weather London', 'news', 'play despacito', 'who is Alan Turing'.");
        } catch (Exception ex) {
            appendBotMessage("An error occurred: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    // --------------- Utilities & HTTP calls ----------------

    private Optional<String> safeJsonString(JSONObject obj, String key) {
        if (obj == null) return Optional.empty();
        return obj.has(key) ? Optional.ofNullable(obj.optString(key)) : Optional.empty();
    }

    private CompletableFuture<String> getWeather(String city) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String q = URLEncoder.encode(city, StandardCharsets.UTF_8);
                String url = "https://api.openweathermap.org/data/2.5/weather?q=" + q + "&appid=" + WEATHER_API_KEY + "&units=metric";
                HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
                HttpResponse<String> resp = httpClient.send(req, BodyHandlers.ofString());
                JSONObject json = new JSONObject(resp.body());
                if (json.has("main")) {
                    JSONObject main = json.getJSONObject("main");
                    double temp = main.getDouble("temp");
                    String desc = json.getJSONArray("weather").getJSONObject(0).getString("description");
                    return "Current temperature in " + city + " is " + temp + "°C with " + desc + ".";
                } else {
                    return null;
                }
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        });
    }

    private CompletableFuture<List<String>> getTopNews() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String url = "https://newsapi.org/v2/top-headlines?country=us&apiKey=" + NEWS_API_KEY;
                HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
                HttpResponse<String> resp = httpClient.send(req, BodyHandlers.ofString());
                JSONObject json = new JSONObject(resp.body());
                if (!json.has("articles")) return Collections.emptyList();
                JSONArray arr = json.getJSONArray("articles");
                List<String> headlines = new ArrayList<>();
                for (int i = 0; i < Math.min(arr.length(), 5); i++) {
                    JSONObject art = arr.getJSONObject(i);
                    headlines.add(art.optString("title", "No title"));
                }
                return headlines;
            } catch (Exception e) {
                e.printStackTrace();
                return Collections.emptyList();
            }
        });
    }

    private CompletableFuture<String> fetchWikipediaSummary(String title) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String t = URLEncoder.encode(title, StandardCharsets.UTF_8);
                String url = "https://en.wikipedia.org/api/rest_v1/page/summary/" + t;
                HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
                HttpResponse<String> resp = httpClient.send(req, BodyHandlers.ofString());
                if (resp.statusCode() != 200) return null;
                JSONObject json = new JSONObject(resp.body());
                return json.optString("extract", null);
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        });
    }

    private void openInBrowser(String url) {
        try {
            if (!url.startsWith("http")) url = "https://" + url;
            Desktop.getDesktop().browse(URI.create(url));
        } catch (IOException e) {
            appendBotMessage("Unable to open browser: " + e.getMessage());
        }
    }

    private List<Integer> extractIntegers(String s) {
        List<Integer> list = new ArrayList<>();
        Scanner sc = new Scanner(s);
        while (sc.hasNext()) {
            if (sc.hasNextInt()) list.add(sc.nextInt());
            else sc.next();
        }
        sc.close();
        return list;
    }

    private boolean containsAny(String s, String... terms) {
        for (String t : terms) if (s.contains(t)) return true;
        return false;
    }

    private <T> T randomFrom(List<T> list) {
        return list.get(new Random().nextInt(list.size()));
    }

    @Override
    public void stop() {
        // cleanup TTS
        if (ttsVoice != null) ttsVoice.deallocate();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
