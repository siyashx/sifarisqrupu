# Lotoreya — qoşulma və yoxlama

23 oktyabr 2026

## Əlavə olunanlar

Admin panelin “Lotoreyalar” bölməsində iki sütunlu kartlar, yaratma və düzəliş forması, şəkil seçimi/yükləmə, Bakı tarixi/saatı, qiymət və toplam bilet sayı var. Yarat düyməsinin solunda fırlanma müddəti dəqiqə/saniyə ilə seçilir (5 saniyə–60 dəqiqə). Bilet sayı 1–99999 ola bilər.

Lotoreyanın məlumatlarında axtarılan, səhifələnən istifadəçi siyahısı böyük, mərkəzlənmiş overlay pəncərəsində açılır. Hər seçilmiş istifadəçiyə bir bilet verilir. Eyni adama növbəti bilet üçün yenidən bilet vermək olar. Kodlar hər lotoreyada 00001-dən başlayır. Mövcud biletlər və sahibləri ayrıca kompakt, səhifələnmiş siyahıda görünür.

Zakaz Qrupunda Ustaların altında Lotoreya düyməsi, iki sütunlu kartlar, faiz/progress, say, tarix, Bilet al → adminlə WhatsApp əlaqəsi, şəxsi bilet kodları və onların sayı var. Bilet verilməsi və lotoreya vəziyyətləri STOMP hadisəsi ilə yenilənir; əlaqə bərpası və periodik sorğular əlavə bərpa yoludur.

Admin çarxa keçəndə əvvəl bildiriş seçimi, daha sonra yalnız Bakı təqvim günü gələcəkdədirsə erkən keçirilmə təsdiqi çıxır. Təsdiqlərdən sonra LIVE vəziyyəti açılır, satış bağlanır, qırmızı ripple indikatoru görünür. Bildiriş üzərindən konkret lotoreyanın yalnız izləmə ekranı açılır. Bildiriş ikonu ilə iştirakçılara təkrar dəvət göndərilə bilər.

Fırlatma serverdə bir dəfə başladılır. Qalib SecureRandom ilə hər biletə bərabər şansla seçilir; başlama (2 saniyə hazırlıq), bitmə və son bucaq bazada saxlanılır. Admin və Flutter eyni cubic yavaşıma funksiyası və server vaxtı ilə animasiya göstərir. Cihazın öz saatı nəticəni seçmir. Gec qoşulan izləyici cari mövqeyə keçir. Səs faylları bu iş üçün yaradılmış WAV effektləridir.

Serverdəki planlayıcı admin tətbiqi bağlansa da nəticəni yekunlaşdırır. Qalib və nəticə animasiyası göstərilir; admin zəng/WhatsApp və mövcud ətraflı profil səhifəsinə keçə bilir. Bitmiş lotoreya yenidən fırlanmır. İştirakçıların bilet kodları daxil olmaqla nəticə bildirişləri göndərilir. Bir neçə bileti olan istifadəçi üçün kodlar bir bildirişdə birləşdirilir (çox böyük kolleksiyada 30 kodlu hissələr); qalib istifadəçiyə ziddiyyətli “uduzdunuz” bildirişi getmir.

## Server və təhlükəsizlik

Yeni paket: `src/main/java/com/codesupreme/sifarisqrupu/lottery/`.

- `lottery` və `lottery_ticket` cədvəlləri əlavə olunur. Hazırkı `spring.jpa.hibernate.ddl-auto=update` onları yaradır. Keçmiş cədvəllər silinmir.
- Bilet verilməsi, satış sayının dəyişməsi və vəziyyət keçidləri tranzaksiyada, lotoreya sətrinə yazma kilidi ilə edilir. Kodun unikal olması bazada da məhdudiyyətlə qorunur. Təkrar sorğunun requestId-si eyni bileti qaytarır.
- Admin API: `/api/v5/push/admin/lotteries`. Mövcud admin açarı ilə, yaxud admin ID/şifrə ilə autentifikasiya tələb olunur. Android açarı JS-ə ötürülmür; native metod yalnız lotoreya yollarına sorğu aça bilər. Native modul olmayan mühit üçün admin ID/şifrə girişi yalnız yaddaşda saxlanılır.
- Üzv API: `/api/v5/lotteries` və `/{id}`. İstifadəçi sessiyası tələb olunur; öz biletləri autentifikasiya olunmuş istifadəçi üzrə seçilir. Digər iştirakçıların telefonları və biletlərinin sahibləri üzv API-də verilmir. Qalibin telefon/profil keçidi yalnız admin cavabındadır.
- `/topic/lotteries` yalnız dəyişən lotoreyanın ID-si və vaxtını bildirir; şəxsi məlumat yayılmır.
- Lotoreya bildirişləri yalnız ZAKAZ tətbiqinə, həqiqi iştirakçılara gedir. Müştəri rolunun iş bildirişi filtri bu şəxsi bildirişləri bloklamır. FCM-in işlək konfiqurasiyası və OS bildiriş icazəsi lazımdır.
- H2 yalnız avtomatik testlər üçün əlavə edilib; production bazası MariaDB olaraq qalır.

## Dəyişən layihələr

1. `/Users/siyashx/IdeaProjects/sifarisqrupu`: yeni lottery paketi və testləri; `pom.xml` (yalnız test H2 asılılığı); admin autentifikasiya şərhinin yenilənməsi. Əvvəlki bildiriş rolu düzəlişləri də bu qovluqda qalır.
2. `/Users/siyashx/Documents/Restore/Emekdaslar/SifarisQrupu/sifarisqrupuadmin`: `src/lottery/`, `App.js`, `src/screens/main/HomeScreen.js`, `AdminPushModule.kt`, `AdminPushState.kt`, yeni `AdminLotteryRoutes.kt`, `assets/lottery_spin.wav`, `assets/lottery_win.wav`, `test/lottery.test.cjs`.
3. `/Users/siyashx/Documents/Restore/Emekdaslar/SifarisQrupu/zakazqrupu`: `lib/features/lottery/`, tətbiq marşrutları, notification deep-link resolver, ana ekrandakı naviqasiya, Android lotoreya bildiriş kanalı adı, audio assets, `pubspec.yaml`/lock və iOS plugin/Pod qeydiyyatları, `test/lottery_test.dart`.

## İcra və deploy

Bu işdə VPS deploy, APK/AAB və iOS build/archive edilməyib. Mövcud dəyişikliklər commit/push edilməyib. Tətbiq versiyaları artırılmayıb.

Əvvəl backend-i əvvəlki ehtiyatlı deploy üsulunuzla yeniləyin. Bazanın ehtiyat nüsxəsini saxlayın. Yeni cədvəllərin yaranmasını və backend-in sağlam açılmasını yoxlayın. Yeni env açarı tələb olunmur: mövcud admin/FCM konfiqurasiyası istifadə olunur.

Adminin Android native modulu dəyişdiyi üçün yeni admin build-i lazımdır; yalnız Metro yenilənməsi native metodu əlavə etmir. Mövcud admin açarı Gradle konfiqurasiyasında olduğu kimi qalmalıdır. Zakaz Qrupunda yeni Flutter build-i lazımdır; iOS üçün `pod install` artıq lokalda icra edilib. Ələhbər və wa-bridge bu funksiya üçün dəyişdirilməyib.

## Avtomatik yoxlamalar

- 52 Flutter testi, o cümlədən 10 lotoreya/bildiriş testi: keçdi.
- 15 lotoreya backend testi: real H2 tranzaksiyası/eyni son bilet üçün paralel sorğular, idempotentlik, tarix yoxlaması, dəyişməz qalib, nəticə bildirişlərinin birləşməsi, şəxsi məlumatlar və API icazələri.
- Əlavə 49 mövcud push testi keçdi.
- 18 admin React/Node testi keçdi. Yeni testlər kartları, bitmiş lotoreyadakı düymələri, overlay axtarışını, native API və ortaq çarx hesabını yoxlayır.
- Native yol filtrinin Kotlin kompilyasiyası və 14 yoxlama keçdi; tam Android build hazırlanmadı.
- 29 iOS dependency/config yoxlaması və `pod install` keçdi.
- Dəyişən Flutter lotoreya fayllarında analiz problemi yoxdur.

## Fiziki cihazlarda son sınaq

1. Admin paneldə 10 biletlik lotoreya yaradın; şəkil və tarixi yoxlayın. Kartda 0/10 görünsün.
2. Zakazda həm müştəri, həm kuryer hesabı ilə Lotoreya bölməsini açın. Bilet al düyməsi admin əlaqəsini və Bakı tarixini göstərsin.
3. Admin paneldə ad/telefon ilə istifadəçi tapıb bilet verin. Kod 00001 olsun; istifadəçi ekranında yenidən girişsiz görünsün. Təkrar bilet kodu 00002 olsun.
4. Satış sayını aşan say və satılmışdan aşağı toplam sayla düzəliş serverdə rədd edilsin. Eyni son bilet iki admin tərəfindən veriləndə yalnız biri uğurlu olsun.
5. Gələcək gün üçün çarxı açın: bildiriş sualı və erkən tarix təsdiqi görünsün. İmtina edəndə LIVE və push başlamasın. Eyni günün gələcək saatında erkən tarix sualı çıxmasın.
6. Bildirişə klik edən istifadəçi həmin lotoreyanın izləmə ekranına keçsin. Digər tətbiqdə Ələhbərə lotoreya bildirişi getməsin.
7. 30 saniyə seçin, çarxı fırladın. Admin və istifadəçi eyni bilet mövqelərini izləsin. İkinci telefonu ortada qoşun; cari mövqedən davam etsin.
8. Admin ekranını bağlayın və ya internetini kəsin: server nəticəni özü bitirsin. Yenidən açanda eyni qalib görünsün, yeni fırlatma düyməsi olmasın.
9. Qalib və uduzan hesabların bildirişlərində öz kodları görünsün. Qalibin bir neçə bileti varsa ona əlavə “uduzdunuz” göndərilməsin.
10. Admin qaliblə zəng/WhatsApp və profil keçidlərini yoxlasın. Səs/mute və tətbiq arxa plana keçəndə səsin dayanmasını hər iki OS-də yoxlayın.

Bu fiziki cihaz sınaqları və production şəbəkəsində canlı sinxronizasiya hələ icra edilməyib. Eyni server zaman xətti istifadə olunur, amma şəbəkə və ekran/audio gecikməsi səbəbindən fiziki cihazlar arasında sıfır millisaniyə fərq vəd edilmir.

## Mağaza təqdimatı haqqında

Tətbiqdə ödəniş emalı qurulmayıb, bilet admin tərəfindən verilir. Əgər real istifadə modelində bilet real pulla satılır və real dəyərli mükafat təsadüfi seçilirsə, adminlə əlaqə axını mağazaların lotoreya tələblərindən azadolma yaratmır. Bu texniki iş lisenziya/mağaza təsdiqi deyil. Apple 5.3 və Google Play real-money qaydaları tətbiq modelinə uyğun ayrıca həll olunmalıdır:

- https://developer.apple.com/app-store/review/guidelines/#gaming-gambling-and-lotteries
- https://support.google.com/googleplay/android-developer/answer/9877032?hl=en


## 23 oktyabr — kartlar, nəticə və bildiriş yeniləməsi

- Hər sətirdə bir kompakt üfüqi kart: solda ekran eninə uyğun 118–168 px şəkil, sağda məlumatlar. Şəkildə yuxarı ortada kiçik tarix nişanı. İstifadəçi şəklə toxunanda 2048 px-ə qədər şəkli böyüdə bilir; kartın qalan hissəsi uyğun lotoreya səhifəsini açır.
- İstifadəçi kartında satış yalnız faiz/progress ilə görünür; bilet kodlarının yanındakı “Cəmi” çıxarılıb. Admin satış sayını görür.
- Filtrlər: Hamısı, Canlı, Yaxında, Aktiv, Bitmiş. Yaxında növbəti 7 günə qədər olan (və gecikmiş) aktiv lotoreyaları əhatə edir. Hamısı sırası: canlılar, ən yaxın tarixlilər, qalan aktivlər yeni yaradılana görə, sonda bitmişlər/ləğv edilənlər. Köhnə backend-də createdAt yoxdursa ID sırası işlənir.
- Nəticə hər girişdə ortada ayrıca, scroll olmayan pəncərədə açılır. Fişənglər kartın önündədir. Qələbə melodiyası yerinə ayrıca lottery_fireworks_v2.wav real fişəng səsi qoşulub; səs mənbəyi asset qovluğundakı LOTTERY_AUDIO.md-dədir. Admin əlaqə seçimləri iki bərabər ikonlu kartdır.
- Admin çarxı native Animated transform ilə fırlanır; hər kadrda React/SVG yenidən qurulmur. Flutter sabit təsviri RepaintBoundary-də saxlayır, yalnız fırlanma qatı dəyişir. Ortaq server vaxtı və cubic yavaşlama qalır. Bütün fiziki cihazlarda sıfır kadr itkisinə zəmanət verilməyib.
- Android foreground FCM mesajı native showNotification körpüsünə də ötürülür. Native eventId filtrinin hesab/rol/expiry/dedup yoxlamaları saxlanılıb. Admin bildiriş cavabı fcmEnabled, participants, queued qaytarır; növbəyə yazılma çatdırılma təsdiqi kimi təqdim edilmir.
- Ana səhifədə Lotoreya düyməsinin sağ yuxarı qırmızı nişanı ACTIVE/LIVE/DRAWING sayını göstərir, sıfırda gizlənir. STOMP və ehtiyat sorğularla yenilənir.
- İstifadəçi kart düymələri sabit 40/42 px ilə məhdudlaşmır; minimum 48 px və böyüyən mətnə uyğun hündürlük verilib.

### Bu dəyişikliklərin yoxlanması

1. Həm tətbiqi, həm admini tam yenidən başladın. Android native showNotification körpüsü hot reload ilə əlavə olunmur: Zakaz Qrupu prosesini dayandırıb flutter run edin. Yeni audio asset-i üçün də tətbiqi yenidən başlatmaq lazımdır.
2. VPS-də yeni backend kodunu ayrıca deploy edin. Bu işdə deploy və mobil release build edilməyib.
3. Bileti olan müştəri ilə Zakaz Qrupunu açıq saxlayın, admindən bildiriş göndərin; sonra arxa planda da yoxlayın. Hesab fərqlidirsə bildiriş göstərilməməlidir. Bildirişə toxunanda həmin lotoreya açılsın. Real FCM çatdırılmasını VPS logları ilə təsdiqləyin.
4. Canlı, yaxın, uzaq tarixli və bitmiş lotoreyaları eyni siyahıda yoxlayın. Birini yaradıb/ləğv edib ana səhifə nişanının yenilənməsinə baxın.
5. Kiçik ekranda və böyük sistem şriftində Bilet al, Canlı izlə, Nəticəyə bax düymələrinin mətnlərini yoxlayın. Şəkli böyüdün, bağlayın və kartın mətninə toxunaraq uyğun səhifəyə keçin.
6. Çarxa gec qoşulun, adminlə eyni biletdə dayanmasını, nəticə pəncərəsini və təkrar girişdə yenidən açılmasını yoxlayın. Səsi bağlama və əlaqə seçimlərinə baxın.


## Bilet geri alma və lotoreyanı silmə

- Admin lotoreya məlumatlarında hər aktiv biletin yanındakı “Geri al” düyməsindən istifadə edir. Təsdiqdən sonra yalnız həmin bilet geri alınır; istifadəçinin bilet siyahısı və satış sayı/faizi canlı yenilənir. Bu əməliyyat yalnız ACTIVE mərhələsində mümkündür.
- Bilet kodları dəyişmir və geri alınmış kod yenidən istifadə olunmur: 00001, 00002, 00003-dən 00002 geri alınarsa, növbəti verilən kod 00004 olur. Ümumi satış limitində geri alınan yer boşalır; hər lotoreya üçün ən böyük kod 99999 olaraq qalır.
- Çarxın wheelTickets sahəsi yalnız qüvvədə olan kodları daşıyır; random seçim aktiv biletlərdən edilir. Admin və istifadəçi klientləri bu siyahını istifadə edir. Kodlar arasında boşluq olduqda düzgün çarx üçün hər iki klient yenilənməlidir.
- Kartdakı və məlumat səhifəsinin başlığındakı zibil qutusu düyməsi lotoreyanı hər iki paneldən çıxarır. İştirakçı biletləri də daha görünmür və ana səhifədə aktiv lotoreya sayı yenilənir. DRAWING zamanı silmə qadağandır; digər mərhələlərdə təsdiqlə mümkündür.
- SQL-də tarixçə qorunur: lottery.deleted/deleted_by/deleted_at və lottery_ticket.revoked/revoked_by/revoked_at. Mövcud ddl-auto=update bu sütunları açılışda yaradır; boolean sahələrin ilkin dəyəri false-dur.
- Admin API: POST /api/v5/push/admin/lotteries/{id}/tickets/{code}/revoke və POST /api/v5/push/admin/lotteries/{id}/delete. Hər ikisi yalnız admin autentifikasiyası ilə işləyir.
- Android native whitelist yeniləndiyi üçün admin tətbiqini yenidən build etmək lazımdır; tək Fast Refresh kifayət etmir. Backend deployu ayrıca edilməlidir. Bu işdə VPS-də silmə, deploy və mobil build icra edilməyib.
- Zakaz Qrupunun lotoreya siyahısı SafeArea ilə sistem naviqasiya düymələrindən ayrılıb. Qalib səsi adminin lottery_fireworks_v2.wav faylından birbaşa kopyalanıb və Flutter-də lottery_winner_admin.wav adı ilə qoşulub. Tətbiqi tam yenidən başladın.

### Əlavə yoxlamalar

- 00002-ni geri alın: 00001/00003 qalsın, növbəti kod 00004 olsun; geri alınan bilet qalib seçilməsin və sahibinə nəticə bildirişi göndərilməsin.
- Təsdiqdən imtina etdikdə əməliyyat olmasın. Eyni bileti paralel geri alma satış sayını iki dəfə azaltmasın. Adi istifadəçi revoke/delete API-lərini çağıra bilməsin.
- Lotoreyanı silin: həm admin, həm istifadəçi siyahısından çıxsın; silinmiş lotoreyanın açıq səhifəsi serverdə 404 qaytarsın. Çarx fırlanarkən silmək mümkün olmasın.
- Android-də 48 px alt naviqasiya sahəsi ilə son kartın aşağı hissəsi tam görünsün.


## 23 oktyabr — etibarsız FCM tokeninin bərpası

Serverdə `UNREGISTERED / NotRegistered` xətası təsdiqləndi. Qeydiyyat sorğusu eyni keşlənmiş tokeni yenidən aktivləşdirə bilirdi. PushDevice indi rədd edilmiş tokenin SHA-256 izini saxlayır (`rejected_token_hash`, nullable); Zakaz qeydiyyatı həmin token üçün `registered:false, tokenRefreshRequired:true` qaytarır və cihazı söndürülmüş saxlayır. Yeni token qəbul edildikdə bu qeyd təmizlənir. Digər tətbiqlərin qeydiyyat protokolu əvvəlki davranışı saxlayır.

Zakaz Qrupu bu cavabda Firebase tokenini silib yenisini alır, yalnız eyni sessiya aktivdirsə yenidən qeydiyyata göndərir. Bir cəhddə ən çox bir yeniləmə edilir; eyni token qaytarıldıqda sonsuz dövr yaranmır. Android bildirişi sistemə göndərilmədən “göstərilib” kimi qeyd olunmur; lotoreya üçün alınma/göstərilməmə səbəbləri token, parol və mesaj mətni olmadan logcat-a yazılır.

Canlı yoxlama: istifadəçi emulyatorda çıxış/giriş edib admin paneldən yenidən bildiriş göndərdi. Yeni token 18:27:31 UTC-də yarandı; `lottery-4-live-6` üçün cihaz qeydi və Android-də aktiv lotoreya bildirişi təsdiqləndi. Bu, manual token yenilənməsinin real nəticəsidir; yeni avtomatik bərpa protokolu lokal testlərdə yoxlanılıb, VPS-ə deploy edilməyib.

Avtomatik bərpanın işləməsi üçün backend və Zakaz Qrupu yenilənməlidir. Native Android kodu da dəyişdiyi üçün tam tətbiq yenidən başladılmalıdır; hot reload kifayət etmir. Bu dəyişiklik üçün admin paneldə yeni kod yoxdur. Testlər: 68 backend, 5 Flutter testi keçdi; Flutter analizi təmizdir.

Android `:app:compileDebugKotlin` yoxlaması da uğurludur (7 dəqiqə 24 saniyə); APK/AAB paketlənməyib.
