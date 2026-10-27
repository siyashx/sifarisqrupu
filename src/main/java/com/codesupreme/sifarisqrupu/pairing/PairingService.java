package com.codesupreme.sifarisqrupu.pairing;

import static com.codesupreme.sifarisqrupu.pairing.PairingState.*;

import com.codesupreme.sifarisqrupu.dao.user.UserRepository;
import com.codesupreme.sifarisqrupu.lottery.LotteryUserDirectory;
import com.codesupreme.sifarisqrupu.model.user.User;
import com.codesupreme.sifarisqrupu.push.FcmPushService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.SecureRandom;
import java.util.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PairingService {

  private final PairingRepository repository;
  private final UserRepository users;
  private final LotteryUserDirectory directory;
  private final ObjectMapper json;
  private final FcmPushService fcm;
  private final SimpMessagingTemplate events;
  private final SecureRandom random = new SecureRandom();
  private final Map<Long, String> drawProgress =
    new java.util.concurrent.ConcurrentHashMap<>();

  public PairingService(
    PairingRepository repository,
    UserRepository users,
    LotteryUserDirectory directory,
    ObjectMapper json,
    FcmPushService fcm,
    SimpMessagingTemplate events
  ) {
    this.repository = repository;
    this.users = users;
    this.directory = directory;
    this.json = json;
    this.fcm = fcm;
    this.events = events;
  }

  public record Form(
    String title,
    String description,
    int groupSize,
    List<Long> userIds,
    int durationSeconds,
    String requestId
  ) {
    public Form(
      String title,
      String description,
      int groupSize,
      List<Long> userIds,
      int durationSeconds
    ) {
      this(title, description, groupSize, userIds, durationSeconds, null);
    }
  }

  public record Start(String title, List<Long> userIds, int durationSeconds) {}

  public record Result(
    String choice,
    Map<Long, Integer> scores,
    int revision
  ) {}

  public record Person(Long id, String name, String avatarUrl) {}

  private static void require(boolean ok, String message) {
    if (!ok) throw new ResponseStatusException(HttpStatus.CONFLICT, message);
  }

  private static String clean(String s) {
    return s == null ? "" : s.strip();
  }

  private Pairing visible(Pairing p) {
    if (p.isDeleted() || p.isTestMode()) throw new ResponseStatusException(
      HttpStatus.NOT_FOUND,
      "Çempionat tapılmadı."
    );
    return p;
  }

  private Pairing get(Long id) {
    return visible(
      repository
        .findById(id)
        .orElseThrow(() ->
          new ResponseStatusException(
            HttpStatus.NOT_FOUND,
            "Çempionat tapılmadı."
          )
        )
    );
  }

  private Pairing locked(Long id) {
    return visible(
      repository
        .lock(id)
        .orElseThrow(() ->
          new ResponseStatusException(
            HttpStatus.NOT_FOUND,
            "Çempionat tapılmadı."
          )
        )
    );
  }

  private PairingState state(Pairing p) {
    try {
      return json.readValue(p.getStateJson(), PairingState.class);
    } catch (Exception e) {
      throw new IllegalStateException("Pairing state invalid", e);
    }
  }

  private void save(Pairing p, PairingState s) {
    try {
      p.setStateJson(json.writeValueAsString(s));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
    repository.saveAndFlush(p);
    publish(p.getId());
  }

  private void publish(Long id) {
    Runnable send = () ->
      events.convertAndSend("/topic/pairings", Map.of("pairingId", id));
    if (
      TransactionSynchronizationManager.isSynchronizationActive()
    ) TransactionSynchronizationManager.registerSynchronization(
      new TransactionSynchronization() {
        @Override
        public void afterCommit() {
          send.run();
        }
      }
    );
    else send.run();
  }

  private Map<Long, Person> people(Collection<Long> ids) {
    Map<Long, Person> result = new LinkedHashMap<>();
    for (User u : users.findAllById(ids))
      result.put(
        u.getId(),
        new Person(
          u.getId(),
          Objects.toString(u.getName(), "İstifadəçi"),
          avatar(u)
        )
      );
    for (Long id : ids)
      result.putIfAbsent(id, new Person(id, "İstifadəçi", null));
    return result;
  }

  public static String avatar(User u) {
    // The first verification photo is the profile portrait; later entries are documents.
    var photos = u.getIdentifyPhoto();
    String portrait = photos == null || photos.isEmpty() ? null : photos.get(0);
    if (portrait != null && portrait.startsWith("https://")) return portrait;
    return u.getProfileImageUrl() != null && u.getProfileImageUrl().startsWith("https://")
      ? u.getProfileImageUrl() : null;
  }

  @Transactional(readOnly = true)
  public Map<String, Object> search(String q, int page) {
    var found = directory.search(
      clean(q).toLowerCase(Locale.ROOT),
      PageRequest.of(Math.max(0, page), 50)
    );
    return Map.of(
      "items",
      found
        .stream()
        .map(u ->
          new Person(
            u.getId(),
            Objects.toString(u.getName(), "İstifadəçi"),
            avatar(u)
          )
        )
        .toList(),
      "pages",
      found.getTotalPages()
    );
  }

  private List<Long> validateUsers(List<Long> ids, int groupSize) {
    require(groupSize >= 2 && groupSize <= 8, "Qrup ölçüsü 2–8 olmalıdır.");
    require(
      ids != null &&
        ids.size() >= groupSize &&
        ids.size() <= 256 &&
        ids.stream().noneMatch(Objects::isNull),
      "2–256 iştirakçı seçin."
    );
    var distinct = new ArrayList<>(new LinkedHashSet<>(ids));
    require(
      distinct.size() == ids.size() && ids.size() % groupSize == 0,
      "İştirakçı sayı qrup ölçüsünə tam bölünməlidir."
    );
    var selected = users.findAllById(ids);
    require(
      selected.size() == ids.size() &&
        selected.stream().noneMatch(u -> Boolean.TRUE.equals(u.getIsDisable())),
      "İştirakçılardan biri mövcud deyil və ya deaktivdir."
    );
    return distinct;
  }

  private void duration(int value, int groups) {
    require(
      value >= Math.max(5, groups * 3) && value <= 3600,
      "Müddət hər qrup üçün ən az 3 saniyə, cəmi 5–3600 saniyə olmalıdır."
    );
  }

  @Transactional
  public Map<String, Object> create(Form f, Long admin) {
    require(
      f != null &&
        !clean(f.title()).isEmpty() &&
        clean(f.title()).length() <= 140 &&
        clean(f.description()).length() <= 4000,
      "Başlıq və açıqlamanı yoxlayın."
    );
    if (f.requestId() != null) {
      require(f.requestId().matches("[a-zA-Z0-9-]{16,64}"), "Yanlış sorğu.");
      var old = repository.findByCreatedByAndRequestId(admin, f.requestId());
      if (old.isPresent()) return view(old.get(), state(old.get()), null);
    }
    var s = new PairingState();
    require(f.groupSize() >= 2 && f.groupSize() <= 8,"Qrup ölçüsü 2–8 olmalıdır.");
    s.participants = f.userIds() == null || f.userIds().isEmpty() ? new ArrayList<>() : validateUsers(f.userIds(), f.groupSize());
    duration(f.durationSeconds(), Math.max(1,s.participants.size() / f.groupSize()));
    var p = new Pairing();
    p.setTitle(clean(f.title()));
    p.setDescription(clean(f.description()));
    p.setGroupSize(f.groupSize());
    p.setDurationSeconds(f.durationSeconds());
    p.setCreatedBy(admin);
    p.setRequestId(f.requestId());
    p.setCreatedAt(System.currentTimeMillis());
    save(p, s);
    return view(p, s, null);
  }

  @Transactional
  public Map<String, Object> edit(Long id, Form f) {
    var p = locked(id);
    var s = state(p);
    require(
      !"DRAWING".equals(p.getStatus()),
      "Çempionat zamanı redaktə bağlıdır."
    );
    require(
      !clean(f.title()).isEmpty() &&
        clean(f.title()).length() <= 140 &&
        clean(f.description()).length() <= 4000,
      "Başlıq və açıqlamanı yoxlayın."
    );
    if (s.rounds.isEmpty()) {
      s.participants = f.userIds() == null || f.userIds().isEmpty() ? new ArrayList<>() : validateUsers(f.userIds(), f.groupSize());
      p.setGroupSize(f.groupSize());
    }
    duration(
      f.durationSeconds(),
      Math.max(1, s.participants.size() / p.getGroupSize())
    );
    p.setDurationSeconds(f.durationSeconds());
    p.setTitle(clean(f.title()));
    p.setDescription(clean(f.description()));
    save(p, s);
    return view(p, s, null);
  }

  @Transactional
  public Map<String,Object> participants(Long id,List<Long> ids) {
    var p=locked(id);var s=state(p);
    require(s.rounds.isEmpty() && "ACTIVE".equals(p.getStatus()),"İlk mərhələ artıq başlayıb. İştirakçıları dəyişmək olmaz.");
    s.participants=validateUsers(ids,p.getGroupSize());p.setAutomatic(true);save(p,s);
    return start(id,new Start(null,null,Math.max(p.getDurationSeconds(),s.participants.size()/p.getGroupSize()*3)));
  }

  @Transactional
  public Map<String, Object> start(Long id, Start request) {
    var p = locked(id);
    var s = state(p);
    require(
      !Set.of("DRAWING", "COMPLETED", "CANCELLED").contains(p.getStatus()),
      "Bu mərhələ başladılan və ya bağlanan vəziyyətdədir."
    );
    require(
      s.rounds.isEmpty() ||
        s.rounds
          .get(s.rounds.size() - 1)
          .games.stream()
          .allMatch(g -> g.confirmed),
      "Əvvəlki mərhələnin bütün hesabları təsdiqlənməlidir."
    );
    List<Long> selected;
    List<Long> placement = new ArrayList<>();
    if (s.rounds.isEmpty()) selected = new ArrayList<>(s.participants);
    else {
      var previous = s.rounds.get(s.rounds.size() - 1);
      require(
        !"FINAL".equals(previous.stage),
        "Final mərhələsi artıq keçirilib."
      );
      selected = new ArrayList<>(previous.byes);
      for (var game : previous.games)
        if (!game.placement) selected.add(game.winnerId);
      if (p.getGroupSize() == 2 && selected.size() == 2) {
        for (var game : previous.games)
          if (!game.placement) for (Long uid : game.users)
            if (!uid.equals(game.winnerId)) placement.add(uid);
        if (placement.size() != 2) placement.clear();
      }
    }
    require(selected.size() >= 2, "Yarışın qalibi artıq müəyyənləşib.");
    // A redraw of an unfinished round cannot silently replace its entrants.
    if (request.userIds() != null && !request.userIds().isEmpty()) require(
      new HashSet<>(request.userIds()).equals(new HashSet<>(selected)),
      "İrəliləyən iştirakçıları sistem müəyyənləşdirir."
    );
    Collections.shuffle(selected, random);
    var round = new Round();
    round.number = s.rounds.size() + 1;
    round.stage =
      selected.size() <= p.getGroupSize()
        ? "FINAL"
        : selected.size() <= p.getGroupSize() * p.getGroupSize()
          ? "SEMIFINAL"
          : "ROUND";
    round.title = "FINAL".equals(round.stage)
      ? "Final"
      : "SEMIFINAL".equals(round.stage)
        ? "Yarımfinal"
        : round.number + "-ci mərhələ";
    if (!clean(request.title()).isEmpty()) round.title = clean(request.title());
    require(round.title.length() <= 140, "Mərhələ adı çox uzundur.");
    for (int i = 0; i < selected.size(); ) {
      int count = Math.min(p.getGroupSize(), selected.size() - i);
      if (count == 1) {
        round.byes.add(selected.get(i));
        break;
      }
      var game = new Game();
      game.index = round.games.size();
      game.users = new ArrayList<>(selected.subList(i, i + count));
      round.games.add(game);
      i += count;
    }
    if (!placement.isEmpty()) {
      var game = new Game();
      game.index = round.games.size();
      game.placement = true;
      game.users = placement;
      round.games.add(game);
    }
    duration(request.durationSeconds(), round.games.size());
    round.startedAt = System.currentTimeMillis() + 2000;
    round.endsAt = round.startedAt + request.durationSeconds() * 1000L;
    s.rounds.add(round);
    p.setDurationSeconds(request.durationSeconds());
    p.setStatus("DRAWING");
    save(p, s);
    notifyUsers(
      p,
      s,
      "participants",
      "live",
      java.util.stream.Stream.concat(selected.stream(), placement.stream())
        .distinct()
        .toList()
    );
    return view(p, s, null);
  }

  private static long revealAt(Round r, int index) {
    return (
      r.startedAt +
      Math.round(((r.endsAt - r.startedAt) * (index + 1.0)) / r.games.size())
    );
  }

  @Transactional
  public void finishAnimation(Long id) {
    var p = locked(id);
    var s = state(p);
    if (!"DRAWING".equals(p.getStatus()) || s.rounds.isEmpty()) {
      drawProgress.remove(id);
      return;
    }
    var r = s.rounds.get(s.rounds.size() - 1);
    long now = System.currentTimeMillis();
    if (now < r.endsAt) {
      long revealed = r.games
        .stream()
        .filter(g -> now >= revealAt(r, g.index))
        .count();
      String progress = r.number + ":" + revealed;
      if (!progress.equals(drawProgress.put(id, progress))) publish(id);
      return;
    }
    drawProgress.remove(id);
    p.setStatus("RESULTS");
    save(p, s);
  }

  @Transactional
  public Map<String, Object> notify(Long id, String audience) {
    return notify(id, audience, null);
  }

  @Transactional
  public Map<String, Object> notify(
    Long id,
    String audience,
    String requestId
  ) {
    var p = locked(id);
    var s = state(p);
    if (requestId != null) {
      require(requestId.matches("[a-zA-Z0-9-]{16,64}"), "Yanlış sorğu.");
      if (s.noticeRequests.containsKey(requestId)) return Map.of(
        "queued",
        s.noticeRequests.get(requestId)
      );
    }
    var result = notifyUsers(p, s, audience, "created", null);
    if (requestId != null) {
      s.noticeRequests.put(
        requestId,
        ((Number) result.get("queued")).intValue()
      );
      while (s.noticeRequests.size() > 100)
        s.noticeRequests.remove(s.noticeRequests.keySet().iterator().next());
      save(p, s);
    }
    return result;
  }

  private Map<String, Object> notifyUsers(
    Pairing p,
    PairingState s,
    String audience,
    String event,
    List<Long> override
  ) {
    require(
      Set.of("all", "participants", "none").contains(audience),
      "Bildiriş seçimini yoxlayın."
    );
    var ids = "all".equals(audience)
      ? users.findNotificationRecipientIds()
      : override != null
        ? override
        : s.participants;
    int queued = 0;
    if (!"none".equals(audience)) {
      String eventId =
        "pairing-" + p.getId() + "-" + event + "-" + UUID.randomUUID();
      for (String app : List.of("ZAKAZ", "ELEHBER"))
        queued += fcm.enqueue(
          app,
          ids,
          "live".equals(event) ? "🔵 Çempionat başlayır" : "🤝 Yeni çempionat",
          p.getTitle() + " — İzləmək üçün bildirişə toxunun.",
          Map.of(
            "scope",
            "pairing",
            "channel",
            "pairing",
            "event",
            event,
            "pairingId",
            p.getId()
          ),
          "live".equals(event) ? 3600 : 86400,
          eventId
        );
    }
    return Map.of("queued", queued);
  }

  private Game game(PairingState s, int roundNumber, int index) {
    require(
      roundNumber > 0 && roundNumber <= s.rounds.size(),
      "Mərhələ tapılmadı."
    );
    var r = s.rounds.get(roundNumber - 1);
    require(index >= 0 && index < r.games.size(), "Çempionat tapılmadı.");
    require(
      System.currentTimeMillis() >= revealAt(r, index),
      "Bu qrup hələ açıqlanmayıb."
    );
    return r.games.get(index);
  }

  @Transactional
  public Map<String, Object> adminChoice(Long id,int round,int index,Long user,Result request) {
    var p=locked(id);var s=state(p);var g=game(s,round,index);
    require(!Set.of("COMPLETED","CANCELLED").contains(p.getStatus()),"Çempionat bağlanıb.");
    require(g.users.contains(user),"İştirakçı bu oyunda deyil.");
    require(!g.confirmed && !g.votes.containsKey(user),"Bu iştirakçı artıq seçim edib.");
    require(request.scores()==null && Set.of("won","lost").contains(Objects.toString(request.choice(),"")),"Seçimi yoxlayın.");
    return result(id,round,index,user,request,false);
  }

  @Transactional
  public Map<String, Object> result(
    Long id,
    int round,
    int index,
    Long user,
    Result request,
    boolean admin
  ) {
    var p = locked(id);
    var s = state(p);
    require(
      !Set.of("COMPLETED", "CANCELLED").contains(p.getStatus()) || admin,
      "Çempionat bağlanıb."
    );
    var g = game(s, round, index);
    require(admin || g.users.contains(user), "Bu qrupda iştirak etmirsiniz.");
    require(admin || !g.confirmed, "Hesab artıq təsdiqlənib.");
    require(
      request.revision() == g.revision,
      "Seçimlər yenilənib. Yenidən yoxlayın."
    );
    if (request.choice() != null) {
      require(
        !admin && Set.of("won", "lost").contains(request.choice()),
        "Nəticə seçimini yoxlayın."
      );
      g.votes.put(user, request.choice());
      long winners = g.votes.values().stream().filter("won"::equals).count();
      if (winners > 1 || (g.votes.size() == g.users.size() && winners != 1)) {
        g.votes.clear();
        g.scores.clear();
        g.confirmations.clear();
        g.revision++;
        g.notice = "Seçimlər uyğun gəlmədi. Yenidən seçin.";
      } else g.notice = "";
    }
    if (admin && request.scores() != null && round < s.rounds.size()) {
      require(
        !"DRAWING".equals(p.getStatus()),
        "Canlı mərhələ zamanı əvvəlki nəticəni dəyişmək olmaz."
      );
      s.rounds = new ArrayList<>(s.rounds.subList(0, round));
      p.setStatus("RESULTS");
    }
    if (request.scores() != null) {
      require(
        request.scores().keySet().equals(new HashSet<>(g.users)) &&
          request
            .scores()
            .values()
            .stream()
            .allMatch(v -> v != null && v >= 0 && v <= 999),
        "Hər iştirakçı üçün 0–999 arasında hesab yazın."
      );
      Long winner;
      if (admin) {
        int max = Collections.max(request.scores().values());
        var first = request
          .scores()
          .entrySet()
          .stream()
          .filter(e -> e.getValue() == max)
          .toList();
        require(
          first.size() == 1,
          "Bir qalib olmalıdır; hesab bərabər qala bilməz."
        );
        winner = first.get(0).getKey();
      } else {
        require(
          g.votes.size() == g.users.size() &&
            g.votes.values().stream().filter("won"::equals).count() == 1,
          "Əvvəl bütün iştirakçılar uddu/uduzdu seçsin."
        );
        winner = g.votes
          .entrySet()
          .stream()
          .filter(e -> "won".equals(e.getValue()))
          .findFirst()
          .orElseThrow()
          .getKey();
        require(
          g.users
            .stream()
            .allMatch(
              u ->
                u.equals(winner) ||
                request.scores().get(winner) > request.scores().get(u)
            ),
          "Hesab seçilmiş qalibə uyğun deyil."
        );
      }
      if (!g.scores.equals(request.scores())) {
        s.rounds.get(round - 1).adminReadyNotified = false;
        g.scores = new LinkedHashMap<>(request.scores());
        g.confirmations.clear();
      }
      if (admin) {
        g.votes.clear();
        for(Long uid:g.users) g.votes.put(uid,uid.equals(winner)?"won":"lost");
        g.confirmations.addAll(g.users);
      }
      else g.confirmations.add(user);
      if (g.confirmations.containsAll(g.users)) {
        g.confirmed = true;
        g.winnerId = winner;
        g.notice = "Hesab təsdiqləndi.";
      }
    }
    if (!s.rounds.isEmpty()) {
      var last = s.rounds.get(s.rounds.size() - 1);
      if (
        "FINAL".equals(last.stage) &&
        last.games.stream().allMatch(game -> game.confirmed)
      ) p.setStatus("COMPLETED");
    }
    if (!p.isAutomatic()) notifyAdminWhenReady(p, s);
    save(p, s);
    if (p.isAutomatic() && !Set.of("COMPLETED","CANCELLED").contains(p.getStatus()) && !s.rounds.isEmpty()) {
      var last=s.rounds.get(s.rounds.size()-1);
      if (!"FINAL".equals(last.stage) && last.games.stream().allMatch(game -> game.confirmed)) {
        p.setStatus("RESULTS");
        return start(id,new Start(null,null,p.getDurationSeconds()));
      }
    }
    return view(p, s, user);
  }

  private void notifyAdminWhenReady(Pairing p, PairingState s) {
    if (s.rounds.isEmpty() || p.getCreatedBy() == null ||
      Set.of("COMPLETED", "CANCELLED").contains(p.getStatus())) return;
    var round = s.rounds.get(s.rounds.size() - 1);
    if ("FINAL".equals(round.stage) || round.adminReadyNotified ||
      round.games.isEmpty() || !round.games.stream().allMatch(g -> g.confirmed)) return;
    // A ready transition and its durable delivery commit with the score update.
    round.adminReadyNotified = true;
    p.setNotificationRound(p.getNotificationRound() + 1);
    fcm.enqueue("ADMIN", List.of(p.getCreatedBy()), "🔵 Növbəti mərhələ hazırdır",
      p.getTitle() + " — " + round.title + " hesabları təsdiqləndi. Növbəti mərhələni başlatmaq üçün toxunun.",
      Map.of("scope", "pairing", "channel", "admin_pairing", "event", "round_ready",
        "pairingId", p.getId(), "roundNumber", round.number, "screen", "PairingDetail"),
      86400, "pairing-" + p.getId() + "-ready-" + round.number + "-" + p.getNotificationRound());
  }

  @Transactional
  public Map<String, Object> reset(Long id, int round, int index) {
    var p = locked(id);
    var s = state(p);
    require(
      !"DRAWING".equals(p.getStatus()),
      "Əvvəl canlı mərhələni gözləyin."
    );
    var g = game(s, round, index);
    if (round < s.rounds.size()) s.rounds = new ArrayList<>(
      s.rounds.subList(0, round)
    );
    s.rounds.get(round - 1).adminReadyNotified = false;
    g.votes.clear();
    g.scores.clear();
    g.confirmations.clear();
    g.winnerId = null;
    g.confirmed = false;
    g.revision++;
    g.notice = "Admin nəticəni yenilədi.";
    if ("COMPLETED".equals(p.getStatus())) p.setStatus("RESULTS");
    save(p, s);
    return view(p, s, null);
  }

  @Transactional
  public Map<String, Object> status(Long id, String status) {
    var p = locked(id);
    var s = state(p);
    require(
      Set.of("ACTIVE", "RESULTS", "COMPLETED", "CANCELLED").contains(status),
      "Yanlış status."
    );
    require(
      !"DRAWING".equals(p.getStatus()),
      "Əvvəl canlı çempionatı gözləyin."
    );
    require(
      !s.rounds.isEmpty() || !"RESULTS".equals(status),
      "Əvvəl bir mərhələ başladın."
    );
    require(
      s.rounds.isEmpty() || !"ACTIVE".equals(status),
      "Bu yarışda mərhələ artıq var."
    );
    require(
      !"COMPLETED".equals(status) ||
        (!s.rounds.isEmpty() &&
          "FINAL".equals(s.rounds.get(s.rounds.size() - 1).stage) &&
          s.rounds
            .stream()
            .allMatch(r -> r.games.stream().allMatch(g -> g.confirmed))),
      "Bütün hesablar təsdiqlənməlidir."
    );
    p.setStatus(status);
    save(p, s);
    return view(p, s, null);
  }

  @Transactional
  public void delete(Long id) {
    var p = locked(id);
    require(!"DRAWING".equals(p.getStatus()), "Canlı çempionatı silmək olmaz.");
    p.setDeleted(true);
    save(p, state(p));
  }

  @Transactional(readOnly = true)
  public Map<String, Object> detail(Long id, Long user) {
    var p = get(id);
    return view(p, state(p), user);
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> list(Long user) {
    return repository
      .findByDeletedFalseOrderByCreatedAtDesc()
      .stream()
      .filter(p -> !p.isTestMode())
      .filter(p -> user == null || !"CANCELLED".equals(p.getStatus()))
      .sorted(
        Comparator.comparingInt((Pairing p) ->
          "COMPLETED".equals(p.getStatus()) ? 1 : 0
        ).thenComparing(
          Comparator.comparingLong(Pairing::getCreatedAt).reversed()
        )
      )
      .map(p -> view(p, state(p), user))
      .toList();
  }

  private Map<String, Object> view(Pairing p, PairingState s, Long viewer) {
    long now = System.currentTimeMillis();
    var people = people(s.participants);
    var rounds = new ArrayList<Map<String, Object>>();
    for (var r : s.rounds) {
      var games = new ArrayList<Map<String, Object>>();
      for (var g : r.games) {
        boolean revealed = now >= revealAt(r, g.index);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("index", g.index);
        row.put("placement", g.placement);
        row.put("size", g.users.size());
        row.put("revealAt", revealAt(r, g.index));
        row.put("revealed", revealed);
        row.put("users", revealed ? g.users : List.of());
        row.put("votes", revealed ? g.votes : Map.of());
        row.put("scores", revealed ? g.scores : Map.of());
        row.put("confirmations", revealed ? g.confirmations : List.of());
        row.put("winnerId", revealed ? g.winnerId : null);
        row.put("confirmed", revealed && g.confirmed);
        row.put("revision", g.revision);
        row.put("notice", g.notice);
        games.add(row);
      }
      rounds.add(
        Map.of(
          "eligibleUserIds",
          java.util.stream.Stream.concat(
            r.games.stream().flatMap(g -> g.users.stream()),
            r.byes.stream()
          )
            .distinct()
            .sorted()
            .toList(),
          "number",
          r.number,
          "title",
          r.title,
          "startedAt",
          r.startedAt,
          "endsAt",
          r.endsAt,
          "games",
          games,
          "stage",
          r.stage,
          "byes",
          r.byes
        )
      );
    }
    var board = new ArrayList<Map<String, Object>>();
    for (Long id : s.participants) {
      int played = 0,
        wins = 0,
        scored = 0,
        against = 0;
      for (var r : s.rounds)
        for (var g : r.games)
          if (g.confirmed && g.users.contains(id)) {
            played++;
            if (id.equals(g.winnerId)) wins++;
            scored += g.scores.getOrDefault(id, 0);
            against += g.scores
              .entrySet()
              .stream()
              .filter(e -> !e.getKey().equals(id))
              .mapToInt(Map.Entry::getValue)
              .sum();
          }
      var row = new LinkedHashMap<String, Object>();
      row.put("user", people.get(id));
      row.put("played", played);
      row.put("wins", wins);
      row.put("losses", played - wins);
      row.put("points", wins * 3);
      row.put("for", scored);
      row.put("against", against);
      row.put("difference", scored - against);
      board.add(row);
    }
    board.sort(
      Comparator.comparingInt(
        (Map<String, Object> row) -> (int) row.get("points")
      )
        .reversed()
        .thenComparing(
          Comparator.comparingInt(
            (Map<String, Object> row) -> (int) row.get("difference")
          ).reversed()
        )
        .thenComparing(
          Comparator.comparingInt(
            (Map<String, Object> row) -> (int) row.get("for")
          ).reversed()
        )
        .thenComparingLong(row -> ((Person) row.get("user")).id())
    );
    for (int i = 0; i < board.size(); i++) board.get(i).put("rank", i + 1);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", p.getId());
    result.put("version", p.getVersion());
    result.put("title", p.getTitle());
    result.put("description", p.getDescription());
    result.put("groupSize", p.getGroupSize());
    result.put("durationSeconds", p.getDurationSeconds());
    result.put("status", p.getStatus());
    result.put("createdAt", p.getCreatedAt());
    result.put("participants", people.values());
    result.put(
      "participating",
      viewer != null && s.participants.contains(viewer)
    );
    result.put("myUserId", viewer);
    result.put("rounds", rounds);
    result.put("leaderboard", board);
    List<Long> next = new ArrayList<>(s.participants);
    List<Long> podium = new ArrayList<>();
    if (!s.rounds.isEmpty()) {
      var last = s.rounds.get(s.rounds.size() - 1);
      next = new ArrayList<>(last.byes);
      for (var game : last.games)
        if (!game.placement && game.confirmed) next.add(game.winnerId);
      if (
        "FINAL".equals(last.stage) &&
        last.games.stream().allMatch(game -> game.confirmed)
      ) {
        var finalGame = last.games
          .stream()
          .filter(g -> !g.placement)
          .findFirst()
          .orElseThrow();
        podium.add(finalGame.winnerId);
        var others = finalGame.users
          .stream()
          .filter(uid -> !uid.equals(finalGame.winnerId))
          .sorted(
            Comparator.comparingInt((Long uid) -> finalGame.scores.get(uid))
              .reversed()
              .thenComparingLong(Long::longValue)
          )
          .toList();
        podium.addAll(others);
        last.games
          .stream()
          .filter(g -> g.placement)
          .findFirst()
          .ifPresent(g -> podium.add(g.winnerId));
        if (podium.size() < 3 && s.rounds.size() > 1) {
          var before = s.rounds.get(s.rounds.size() - 2);
          before.games
            .stream()
            .filter(g -> !g.placement)
            .flatMap(g ->
              g.users.stream().filter(uid -> !uid.equals(g.winnerId))
            )
            .filter(uid -> !podium.contains(uid))
            .findFirst()
            .ifPresent(podium::add);
        }
        next.clear();
      }
    }
    if (!podium.isEmpty()) {
      board.sort(
        Comparator.comparingInt(row -> {
          int position = podium.indexOf(((Person) row.get("user")).id());
          return position < 0 ? Integer.MAX_VALUE : position;
        })
      );
      for (int i = 0; i < board.size(); i++) board.get(i).put("rank", i + 1);
    }
    result.put("nextUserIds", next);
    result.put("podium", podium.stream().map(people::get).toList());
    result.put("serverNow", now);
    return result;
  }
}
