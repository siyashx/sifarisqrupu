# Çempionat və reklam funksiyaları

## Deploy və ilkin sazlama

Backend-in `ddl-auto=update` sazlaması `community_ad_plan`, `community_ad_request`, `community_ad_visit` cədvəllərini və çempionatın `automatic` sahəsini yaradır. Cədvəllər üçün ayrıca əl ilə SQL əmri tələb olunmur. Mövcud cütləşmə URL-ləri, bilet və hesab məlumatları uyğunluq üçün saxlanılır; istifadəçiyə göstərilən adlar Çempionatdır.

Əvvəl backend deploy olunmalıdır. Sonra admin Android tətbiqi və hər iki Flutter tətbiqi yenidən build edilməlidir. Flutter-də `video_player` asılılığı, iki səssiz 4 saniyəlik dövr edən MP4 banner və WhatsApp işarəsi üçün font əlavə olunub. iOS-da yeni plugin üçün adi Flutter/CocoaPods hazırlığı build zamanı işləməlidir.

Admin panel → Reklam Qiymətləri bölməsində Alqı-satqı və Çat üçün ayrıca paketlər yaradın. Paket başlığı, müddət (saat), AZN qiyməti, istəyə bağlı qüvvədə olma tarixləri idarə edilir. Qiymətlər göstərilən təklifdir; tətbiqdə ödəniş tutulmur.

Reklam sorğusu bildirişləri mövcud `push.admin.recipient-ids` sazlamasındakı adminlərə gedir (standart: 1). Sənəd şəkilləri, parollar və şəxsi giriş məlumatları bu bildirişlərə daxil edilmir.

## Çempionat

Yeni çempionat başlıq/açıqlama/qrup ölçüsü ilə boş yaradılır. İstifadəçilər adminlə əlaqə düyməsindən ad və nömrəsi daxil edilmiş WhatsApp mətni açırlar. Admin məlumatlar səhifəsindən iştirakçıları seçib “Seçimi tamamla” basanda ilk mərhələ avtomatik başlayır. Seçim pəncərəsini geri/bağla ilə bağlamaq çempionatı başlatmır.

İştirakçı sayı qrup ölçüsünə tam bölünməlidir. Son oyunların eyni hesabı bütün tərəflər tərəfindən təsdiqlənəndə növbəti mərhələ avtomatik başlayır. Final və 3-cü yer oyunu əvvəlki qaydalarla hesablanır. Uddum/Uduzdum seçimi hesab sürüşdürücülərini avtomatik açır; server yenə qarşılıqlı seçim və təsdiqləri yoxlayır. Əvvəlki `/start` API-si uyğunluq üçün qalır, yeni admin interfeysində əl ilə başlatma düyməsi yoxdur.

## Alqı-satqı və çat reklamı

Mənim elanlarım üfüqi göstərilir; oxlar yalnız həmin istiqamətdə başqa elan varsa görünür. Elan kartı məlumatlar səhifəsinə açılır. Şəkillər, başlıq, açıqlama, qiymət və aşağıda zəng/WhatsApp düymələri var. Hər elanı, başqa istifadəçinin elanını da, irəli çəkmək üçün müraciət etmək mümkündür.

Yeni elan pulsuz yaradıla bilər. Reklam paketi seçilərsə, elan yaradıldıqdan sonra reklam sorğusu saxlanılır və adminə göndərmək üçün WhatsApp şablonu açılır. Mövcud elanı irəli çəkməkdə paket seçilir. Reklam admin təsdiqindən sonra başlayır. Admin qəbul etməyincə elan ödənişli bölmədə görünmür. Admin təsdiqi, geri çəkmə və müddətin bitməsi yayımı idarə edir.

Çat reklamında başlıq, istəyə bağlı açıqlama/şəkil, təyinat (profil nömrəsinə WhatsApp və ya HTTPS link), paket seçilir; reklam admin təsdiqindən sonra başlayır. Önizləmə reklamın çatda görünüşünü göstərir. Sorğu saxlananda adminə başlıq, müddət və qiymətlə bildiriş gedir. Admin sorğunu qəbul/rədd edir, detalları və əlaqə seçimlərini görə bilir. Təsdiqlənmiş və vaxtı çatmış reklamlar uyğun Suşi/Moto çatında hər 5 mesajdan sonra reklam etiketi ilə yerləşdirilir. Bunlar sifariş sayına daxil edilmir və sifariş bildirişi yaratmır.

Qiymət və status yenilənmələri `/topic/advertising` vasitəsilə invalidasiya edilir, aktiv səhifələrdə ehtiyat yenilənmə də var. Sorğuda qiymət/müddət nüsxəsi saxlanılır; qiymət sonradan dəyişsə əvvəlki sorğuya tətbiq olunmur. Aktiv paket versiyası dəyişəndə köhnə seçimlə yeni sorğu rədd olunur və yenidən seçim istənir. Təkrar eyni sorğu açarı yeni reklam yaratmır; təkrar təsdiq müddəti uzatmır.

Alqı-satqının günlük giriş sayı **real, hesab üzrə unikal** girişlərdir. Eyni hesabın iki tətbiqdə girişi həmin gün bir dəfə sayılır; gün Bakı vaxtı ilə dəyişir. Rəqəm təsadüfi artırılmır. Sorğulardakı şəxsin adı/telefonu yalnız admin idarəetməsi və istifadəçinin öz əlaqə şablonu üçündür; reklamın ümumi lentinə ödəyicinin profili əlavə olunmur.

Bu dəyişiklik istehsal serverinə avtomatik deploy etmir və mağaza üçün release faylı yaratmır. Tətbiq versiyası 2.2.0 (31) saxlanılıb.

## Admin updates (27 October 2026)

Ad durations start when the admin approves the request. Pending cards show the request timestamp and no invented start time. Repeated approval does not extend the duration. Dates are rendered in Baku time without timezone labels.

Admin ad cards open details on tap, images open a full-screen viewer, and blue usernames open user details. Courier, chat-ad and listing-ad badges use the authenticated `/api/v5/push/admin/dashboard-counts` endpoint and count pending requests only. Counts refresh on focus, relevant push events and a 15-second fallback timer. Chat and listing request notifications have distinct native icons.

Admin chats now use inverted per-message rows: offset zero is the newest message, with no animated scroll-to-end on opening. The viewport remains anchored while older history is being read.
