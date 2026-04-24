

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.TimeUnit;


public class QuizLeaderboardSystem {

    // ─── Configuration ────────────────────────────────────────────────────────
    private static final String BASE_URL  = "https://devapigw.vidalhealthtpa.com/srm-quiz-task";
   private static final String REG_NO = "RA2311008050052";   
    private static final int  TOTAL_POLLS      = 10;
    private static final int POLL_DELAY_SECS  = 5;


    // Key = "roundId|participant"  →  ensures deduplication
    private final Set<String>          seenEvents     = new HashSet<>();
    // Key = participant name       →  running total score
    private final Map<String, Integer> scoreBoard     = new LinkedHashMap<>();

    private final HttpClient     httpClient = HttpClient.newHttpClient();
    private final ObjectMapper   mapper     = new ObjectMapper();


    public static void main(String[] args) throws Exception {
        new QuizLeaderboardSystem().run();
    }

    private void run() throws Exception {
        System.out.println("=== Quiz Leaderboard System Started ===");
        System.out.println("Registration No: " + REG_NO);
        System.out.println();

      
        pollAll();

        //Build sorted leaderboard
        List<Map<String, Object>> leaderboard = buildLeaderboard();

        // Print summary
        printLeaderboard(leaderboard);

      
        submitLeaderboard(leaderboard);
    }

    
    private void pollAll() throws Exception {
        for (int poll = 0; poll < TOTAL_POLLS; poll++) {
            System.out.printf("Polling index %d/%d ...%n", poll, TOTAL_POLLS - 1);

            try {
                String url = String.format(
                        "%s/quiz/messages?regNo=%s&poll=%d", BASE_URL, REG_NO, poll);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .GET()
                        .header("Accept", "application/json")
                        .build();

                HttpResponse<String> response =
                        httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                System.out.printf("  HTTP %d%n", response.statusCode());

                if (response.statusCode() == 200) {
                    processResponse(response.body(), poll);
                } else {
                    System.out.println("  WARNING: Non-200 response, skipping poll " + poll);
                }

            } catch (Exception e) {
                System.err.println("  ERROR on poll " + poll + ": " + e.getMessage());
            }

            // 5-second delay
            if (poll < TOTAL_POLLS - 1) {
                System.out.println("  Waiting " + POLL_DELAY_SECS + "s before next poll...");
                TimeUnit.SECONDS.sleep(POLL_DELAY_SECS);
            }
        }
    }

    // Deduplicate and aggregate
    private void processResponse(String body, int pollIndex) throws Exception {
        JsonNode root = mapper.readTree(body);
        JsonNode events = root.path("events");

        if (!events.isArray()) {
            System.out.println("  No events array found in response.");
            return;
        }

        int newEvents  = 0;
        int dupEvents  = 0;

        for (JsonNode event : events) {
       String roundId  = event.path("roundId").asText().trim();
String participant = event.path("participant").asText().trim();
            int    score       = event.path("score").asInt();

            // create unique key for each event
            String dedupeKey = roundId + "|" + participant;

            if (seenEvents.contains(dedupeKey)) {
                // Duplicate ignore
                dupEvents++;
            } else {
                // New event record and aggregate
                seenEvents.add(dedupeKey);
                scoreBoard.merge(participant, score, Integer::sum);
                newEvents++;
            }
        }

        System.out.printf("  Events: %d new, %d duplicates ignored%n", newEvents, dupEvents);
    }

    // Build sorted leaderboard 
    private List<Map<String, Object>> buildLeaderboard() {
        List<Map<String, Object>> leaderboard = new ArrayList<>();

        for (Map.Entry<String, Integer> entry : scoreBoard.entrySet()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("participant", entry.getKey());
            item.put("totalScore",  entry.getValue());
            leaderboard.add(item);
        }

        // Sort descending by totalScore;
        leaderboard.sort((a, b) -> {
            int scoreDiff = (int) b.get("totalScore") - (int) a.get("totalScore");
            if (scoreDiff != 0) return scoreDiff;
            return ((String) a.get("participant")).compareTo((String) b.get("participant"));
        });

        return leaderboard;
    }

    //Submit leaderboard 
    private void submitLeaderboard(List<Map<String, Object>> leaderboard) throws Exception {
        System.out.println("\n=== Submitting Leaderboard ===");

        ObjectNode payload = mapper.createObjectNode();
        payload.put("regNo", REG_NO);

        ArrayNode lbArray = mapper.createArrayNode();
        for (Map<String, Object> entry : leaderboard) {
            ObjectNode node = mapper.createObjectNode();
            node.put("participant", (String) entry.get("participant"));
            node.put("totalScore",  (int)    entry.get("totalScore"));
            lbArray.add(node);
        }
        payload.set("leaderboard", lbArray);

        String requestBody = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload);
        System.out.println("Payload:\n" + requestBody);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/quiz/submit"))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .build();

        HttpResponse<String> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        System.out.println("\nSubmission Response (HTTP " + response.statusCode() + "):");
    
        try {
            JsonNode respNode = mapper.readTree(response.body());
            System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(respNode));
        } catch (Exception e) {
            System.out.println(response.body());
        }
    }

    
    private void printLeaderboard(List<Map<String, Object>> leaderboard) {
        System.out.println("\n=== Final Leaderboard ===");
        System.out.printf("%-5s %-20s %s%n", "Rank", "Participant", "Total Score");
        System.out.println("-".repeat(40));

        int rank         = 1;
        long grandTotal  = 0;

        for (Map<String, Object> entry : leaderboard) {
            String participant = (String) entry.get("participant");
            int totalScore  = (int)    entry.get("totalScore");
            System.out.printf("%-5d %-20s %d%n", rank++, participant, totalScore);
            grandTotal += totalScore;
        }

        System.out.println("-".repeat(40));
        System.out.printf("%-5s %-20s %d%n", "", "GRAND TOTAL", grandTotal);
        System.out.println();
    }
}
