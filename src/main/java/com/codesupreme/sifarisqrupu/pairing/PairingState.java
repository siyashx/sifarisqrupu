package com.codesupreme.sifarisqrupu.pairing;

import java.util.*;

/** No future draw assignment is included in a public response. */
public class PairingState {

  public Map<String, Integer> noticeRequests = new LinkedHashMap<>();
  public List<Long> participants = new ArrayList<>();
  public List<Round> rounds = new ArrayList<>();

  public static class Round {

    public int number;
    public String title;
    public String stage = "ROUND";
    public List<Long> byes = new ArrayList<>();
    public long startedAt, endsAt;
    public List<Game> games = new ArrayList<>();
  }

  public static class Game {

    public int index;
    public boolean placement;
    public List<Long> users = new ArrayList<>();
    public Map<Long, String> votes = new LinkedHashMap<>();
    public Map<Long, Integer> scores = new LinkedHashMap<>();
    public Set<Long> confirmations = new LinkedHashSet<>();
    public Long winnerId;
    public boolean confirmed;
    public int revision;
    public String notice = "";
  }
}
