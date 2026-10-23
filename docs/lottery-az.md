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

- 48 Flutter testi, o cümlədən 6 lotoreya testi: keçdi.
- 13 lotoreya backend testi: real H2 tranzaksiyası/eyni son bilet üçün paralel sorğular, idempotentlik, tarix yoxlaması, dəyişməz qalib, nəticə bildirişlərinin birləşməsi, şəxsi məlumatlar və API icazələri.
- Əlavə 49 mövcud push testi keçdi.
- 15 admin React/Node testi keçdi. Yeni testlər iki sütunlu kartları, bitmiş lotoreyadakı düymələri, overlay axtarışını, native API və ortaq çarx hesabını yoxlayır.
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
