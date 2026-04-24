# Quiz Leaderboard System

## Problem Summary

A Java application that:

1. Polls an external validator API **10 times** (poll indices 0–9)
2. **Deduplicates** events using a composite key `(roundId + participant)`
3. **Aggregates** scores per participant
4. Generates a **leaderboard sorted by totalScore (descending)**
5. Submits the leaderboard via POST API

---

## Architecture / Design Decisions

### Deduplication Strategy

The API may return duplicate events across polls.
Each event is uniquely identified using:

```
dedupeKey = roundId + "|" + participant
```

A `HashSet<String>` is used to ensure:

* Duplicate events are ignored
* Only unique events contribute to scoring

This models real-world distributed systems with **at-least-once delivery**.

---

### Score Aggregation

A `Map<String, Integer>` stores total scores per participant.

```java
scoreBoard.merge(participant, score, Integer::sum);
```

---

### Leaderboard Sorting

* Sorted by `totalScore` (descending)
* Alphabetical order used as tie-breaker

---

### HTTP Client

Uses Java 11 built-in `HttpClient` (no external HTTP library required).

---

## Prerequisites

| Tool | Version |
| ---- | ------- |
| Java | 11+     |

---

## How to Run

1. Download required Jackson JAR files:

   * jackson-databind
   * jackson-core
   * jackson-annotations

2. Compile:

```
javac -cp ".;jackson-databind-2.15.2.jar;jackson-core-2.15.2.jar;jackson-annotations-2.15.2.jar" QuizLeaderboardSystem.java
```

3. Run:

```
java -cp ".;jackson-databind-2.15.2.jar;jackson-core-2.15.2.jar;jackson-annotations-2.15.2.jar" QuizLeaderboardSystem
```

---

## Sample Output

```
=== Final Leaderboard ===
Rank  Participant          Total Score
----------------------------------------
1     Diana                470
2     Ethan                455
3     Fiona                440
----------------------------------------
      GRAND TOTAL          1365
```

---

## Key Concepts Used

* Java 11 `HttpClient`
* `HashSet` for deduplication
* `Map.merge()` for aggregation
* Custom sorting using `Comparator`
* Jackson `ObjectMapper` for JSON handling

---

## Author

Madhumathi R
