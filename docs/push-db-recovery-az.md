# Bildirişlər: MariaDB bağlantısı və gecikən sifariş filtrləri — 24.10.2026

## Təsdiqlənən server problemi

VPS-də `wait_timeout=10`, `interactive_timeout=50`, `max_connections=200` idi. `Max_used_connections=35`, RAM available 3.8 GiB, MariaDB uptime 482660 saniyə; son 45 dəqiqədə OOM/restart qeydi yox idi. Backend və `fcm-scheduled-1` loglarında MariaDB bağlantısının bağlanması, `Connection reset by peer`, `Failed to validate connection`, `CannotCreateTransactionException` görünürdü. `wait_timeout=10` Hikari-nin standart bağlantı ömrü ilə uyğun deyildi. Bu, göndəriş işçisində təsdiqlənən nasazlıqdır; ayrı-ayrı telefonlara faktiki çatdırılmanın sübutu deyil.

İstifadəçi `SET GLOBAL wait_timeout=600; SET GLOBAL interactive_timeout=600` tətbiq edib və hər iki dəyərin 600 olduğunu yoxlayıb. GLOBAL dəyişiklik yeni sessiyalara aiddir; mövcud sessiyalar əvvəlki limiti saxlayır. Daimi fayl və yenilənmiş backend hələ VPS-də tətbiq olunmalıdır.

## Dəyişikliklər

- Hikari maxLifetime 300 saniyə, keepalive 60 saniyə. MariaDB idle limiti ən az 600 saniyə olmalıdır. Pool ölçüsü artırılmır.
- Bağlantı gözləmə limiti 10 saniyə, yoxlama limiti 3 saniyə. Hibernate SQL axını söndürülüb; tətbiq və FCM xəbərdarlıqları qalır.
- Göndəriş işçisi `DataAccessException` və `TransactionException` xətalarını ayrıca qarşılayır. Qalan cihazlar emal edilir, bazaya yazılmamış PENDING sətirlər sonrakı dövrdə yenidən oxunur. Baza kəsilməsi Firebase-in 8 cəhd limitini azaltmır. Kəsilmə logu maksimum dəqiqədə bir dəfə, bərpa ayrıca yazılır.
- Lotoreya üçün transaction commit-dən sonra hədəf sayı loglanır. Hədəf yoxdursa ayrıca xəbərdarlıq çıxır. Canlı/nəticə mesajının Firebase tərəfindən qəbul edilməsi və ötürülməməsinin səbəbi event/device ID ilə görünür. Token, parol və bildiriş mətni loglanmır.
- Lotoreyanın tamamlanması və nəticə mesajlarının hazırlanması eyni transaction-da qalır. Baza xətasında hər ikisi geri qaytarılır və planlaşdırıcı təkrar cəhd edir.

`SENT` / `FCM lottery accepted` yalnız Firebase-in mesajı qəbul etdiyini göstərir. Telefonda göstərildiyini göstərmir. TTL bitibsə, cihaz deaktivdirsə və ya Firebase tokeni UNREGISTERED-dirsə, bu mesajın çatması vəd edilmir. Köhnə FAILED/EXPIRED sətirlər toplu şəkildə təkrar göndərilmir.

## Yeni sifarişlər üçün səssiz/offline seçimi

Əvvəl alıcı siyahısı hazırlanarkən mute/online yoxlanırdı, amma göndərişədək keçən müddətdə dəyişmiş seçim yoxlanmırdı. Artıq FCM-ə göndərilməzdən əvvəl:

- ZAKAZ daxil olan sifariş/çat iş bildirişləri üçün həmin istifadəçinin `ZAKAZ / ORDER_GROUP_PUSH` seçimini yenidən oxuyur.
- ELEHBER iş bildirişləri üçün istifadəçinin cari `online` dəyərini yenidən oxuyur. Çat bildirişləri ayrıca `ELEHBER / MOTOTAXI_CHAT` seçiminə də tabedir. Çat mute seçimi birbaşa xəritədən gələn sifariş təklifinə tətbiq edilmir; həmin təklif üçün online və aktiv təklif yoxlanır.
- Lotoreya, alqı-satqı, şəxsi sifariş statusları və səsi dayandıran idarəetmə mesajları bu yeni iş filtrindən keçmir. Ümumi admin mesajları ayrıca `event=notice` ilə fərqləndirilir.
- ZAKAZ mobil mute servisi yeni API-də timeout/500 olduqda köhnə siyahıya yazaraq saxta uğur göstərmir. Köhnə fallback yalnız status API-si 404/405 olduqda istifadə olunur. Müasir API uğursuz olarsa mövcud xəta mesajı göstərilir və UI mute edilmiş kimi dəyişmir.

Firebase-ə artıq verilmiş mesaj sonradan cihaz şəbəkəyə qayıdanda çata bilər; göndərişdən sonrakı bir mute əməliyyatı Firebase-dəki mesajı geri çağıra bilmir. Yeni filtr backend-də hələ gözləyən və bundan sonra yaranan iş bildirişlərinə aiddir.

## VPS addımları

1. `ops/configure-push-db-timeouts.sh` faylını VPS-ə göndərin və root ilə işlədin. Aktiv include yolu yoxlanır, əvvəlki eyniadlı fayl varsa backup alınır; `/etc/mysql/mariadb.conf.d/99-zakaz-push-timeouts.cnf` yaradılır. Yalnız timeout seçimləri ekrana çıxır, GLOBAL ayarlar tətbiq edilir. Baza restart edilmir.
2. Yenilənmiş backend-i mövcud təhlükəsiz deploy üsulu ilə qaldırın. Yeni konteyner əvvəlki 10 saniyəlik sessiyaları saxlamır. Yeni schema/env lazım deyil. Zakaz mobil mute səhvinin düzəlişi üçün tətbiqi yeni Dart kodu ilə build edin; Elehber filtri backend-dəndir.
3. Emulyator/telefon açıldıqda biletli istifadəçiyə canlı bildirişi və nəticəni yoxlayın. Köhnə vaxtı keçmiş canlı bildirişin öz-özünə gəlməsini gözləməyin; admin yenidən göndərməlidir.

Log:
```bash
docker logs --since 10m sifarisqrupu-app 2>&1 | grep -E 'Lottery push|FCM lottery|FCM database|FCM delivery failed|finalization pending retry' | tail -60
```

Konkret lotoreya üçün token/parol çıxarmadan oxuma:
```bash
bash ops/check-lottery-push.sh LOTOREYA_ID
# Yalnız bir hesabın cihazları və bildirişləri:
bash ops/check-lottery-push.sh LOTOREYA_ID USER_ID
```

## Mənbələr

[HikariCP konfiqurasiyası](https://github.com/brettwooldridge/HikariCP#configuration-knobs-baby): maxLifetime baza/şəbəkə limitindən qısa olmalı, keepalive maxLifetime-dan kiçik olmalıdır; maxLifetime üçün minimum 30 saniyədir. Buna görə 10 saniyəlik MariaDB limitini təkcə pool ömrünü azaltmaqla həll etmək olmur.

[MariaDB wait_timeout və GLOBAL/SESSION dəyişənləri](https://mariadb.com/docs/server/server-management/variables-and-modes/server-system-variables#wait_timeout).

[Firebase UNREGISTERED](https://firebase.google.com/docs/cloud-messaging/error-codes): etibarsız token ayrıca problemdir; həmin tokenə təkrar göndəriş onu bərpa etmir. Hazırkı tətbiqlərdə serverin refresh siqnalı ilə token bərpası əvvəlki dəyişikliklərdə mövcuddur.

## Yoxlama nəticəsi

115 backend push/lotoreya/pool/preference testi və 8 Zakaz Flutter mute/role testi keçdi. Dəyişən Dart mənbələri analizdən xəbərdarlıqsız keçdi; hər iki ops skriptinin Bash sintaksisi yoxlandı. Emulyatorlar açılmadı, VPS deployu edilmədi. Yoxlama logları: `/Users/siyashx/Documents/Codex/lottery-push-db-resilience-2026-10-24/`.
