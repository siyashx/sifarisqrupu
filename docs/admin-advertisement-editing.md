# Admin reklam redaktəsi və silinməsi

Admin paneldə çat və elan reklamlarının həm kartında, həm də ətraflı məlumatlarında “Düzəlt” və “Sil” var. Redaktə: başlıq, açıqlama, ImgBB-yə yüklənən şəkil, müddət (gün), qiymət; çat reklamında əlavə olaraq WhatsApp/link təyinatı. Elan reklamının əsas `listingId` və istifadəçisi dəyişmir, orijinal alqı satqı elanı dəyişdirilmir.

Admin API:
- `PUT /api/v5/push/admin/advertising/requests/{id}`: EditRequest, mövcud `version` mütləqdir. Köhnə versiyadan redaktə 409 qaytarır.
- `DELETE /api/v5/push/admin/advertising/requests/{id}`: idempotent soft delete (`status=DELETED`). Reklam admin listindən, istifadəçinin “Reklamlarım” siyahısından və public feed-dən çıxır; elan özü saxlanır. Köhnə bildiriş/sorğu audit məlumatları qorunur, köhnə ID-ni qəbul etməklə yenidən aktivləşdirmək mümkün deyil.

Aktiv reklamı redaktə etmək statusu və yayım başlanğıcını dəyişmir. Yeni müddət ilkin `startsAt` üzərindən hesablanır. Gözləyən reklam yenə təsdiq gözləyir. Feed cache və STOMP yenilənməsi mövcud mexanizmlə işləyir. İstifadəçi reklam redaktə/silmə endpointlərini çağıra bilməz, admin autentifikasiyası tələb olunur.

Backend deployu və admin Android tətbiqinin yenidən build olunması lazımdır: native route allowlist-ə iki dəqiq endpoint əlavə edilib. Bu işdə deploy, commit və push edilməyib.

# Reklam yaratmaq, yeniləmək və təkrar sorğular

- Admin `POST /api/v5/push/admin/advertising/chat` ilə mövcud aktiv istifadəçi və hazırkı CHAT paketi seçib dərhal aktiv reklam yaradır. ImgBB şəkil seçimi mövcud uploader-dən istifadə edir; yükləmə bitənədək əvvəlki şəkil qalır və saxla/bağla bağlıdır.
- `POST /api/v5/push/admin/advertising/requests/{id}/reactivate` bitmiş, geri çəkilmiş və ya rədd edilmiş reklamı hazırkı uyğun SHOP/CHAT paketi ilə indi başladır. Reklam və paketin versiyaları yoxlanır. Silinmiş reklam və deaktiv elan yenilənmir.
- İstifadəçi `GET /api/v5/advertising/promotion/{listing}` ilə yalnız öz gözləyən/aktiv elan reklamını görə bilər. Yeni SHOP sorğusu listing sətiri üzərində DB kilidi alır və eyni istifadəçi+elan üçün açıq sorğu varsa onu qaytarır: yeni qeyd və ikinci admin bildirişi yaratmır. Müddəti bitmiş/rədd edilmiş/geri çəkilmiş sorğu yenisinə mane olmur. Admin köhnəni yeniləyəndə də başqa açıq sorğu yoxlanır.
- Mobil CHAT sorğusu optional `whatsappNumber` qəbul edir; köhnə müştərilər üçün profil nömrəsi əvvəlki kimi işləyir. Profil dəyişmir.
- Mobil sorğular WhatsApp-a başlıq, müddət, qiymət, ad və telefonla keçir; ID, sorğu nömrəsi və texniki başlanma mətni yoxdur. WhatsApp xətası saxlanmış sorğunu təkrar yaratmır.

# Çempionatda ilk mərhələnin başlanması

Admin iştirakçıları `PUT /pairings/{id}/participants` ilə saxlayır. Bu əməliyyat mərhələ başlatmır və canlı bildiriş göndərmir. İlk mərhələdən əvvəl siyahı təkrar dəyişdirilə bilər; hətta say hələ qrup ölçüsünə bölünməsə də saxlanır. Boş siyahı da saxlanır.

İlk mərhələ ayrıca `POST /pairings/{id}/start` ilə, admin təsdiqindən sonra başlayır. Başlama zamanı say qrup ölçüsünə tam bölünməli, bütün seçilənlər mövcud və aktiv olmalıdır. Başladıqdan sonra iştirakçıları dəyişmək qadağandır. Sonrakı mərhələlər hesablar təsdiqləndikcə əvvəlki kimi avtomatik davam edir.

Yeniliklər üçün backend deployu, admin tətbiqinin rebuild-i və hər iki mobil tətbiqin yenilənməsi lazımdır. Deploy, commit və push edilməyib.

# Çat reklamlarının bütün bölmələrdə göstərilməsi

CHAT reklamları artıq yaradıldığı kanalla məhdudlaşmır. Təsdiqlənmiş və müddəti bitməmiş bütün çat reklamları Zakaz Qrupu ana səhifəsində və Moto Taksi bölməsində, Ələhbərin Suşi və Moto Taksi çatlarında göstərilir. `channel` köhnə tətbiqlərlə uyğunluq və sorğunun mənbəyi üçün saxlanır; yayımı məhdudlaşdırmır. İstifadəçinin “Reklamlarım” siyahısı da hər iki kanal üzrə öz çat reklamlarını birgə göstərir. Başqa istifadəçinin şəxsi sorğuları göstərilmir.

Mobil tətbiqlərdə hər beşinci mesajdan sonra, son tam beşlik daxil olmaqla reklam yerləşdirilir. Aktiv reklamlar yaranma tarixinə görə yenidən köhnəyə doğru dövr edir. Şəkil kəsilmədən göstərilir, hündürlüyü 100 məntiqi piksellə məhdudlaşır və toxunanda böyüdülə bilir. Backend deployu və hər iki tətbiqin yenidən build edilməsi tələb olunur.

Admin yaratma/redaktə formasında çat bölməsi seçimi çıxarılıb. Mobil formada da ayrıca Suşi/Moto Taksi bölməsi adı göstərilmir; mətn “Bütün çatlarda hər 5 mesajdan bir görünəcək” şəklindədir.

Çat reklamının başlığı iki sətir, açıqlaması iki sətir göstərilir. “Davamını oxu” bütün açıqlamanı ayrıca pəncərədə açır; uzun mətn kartı böyütmür. Tarix ayırıcıları və reklamlar beşlik hesabına daxil deyil, siyahıdakı adi mesajlar 5, 10, 15… sırası ilə sayılır.

# Sabit reklam mövqeyi və sürüşən tarixçə

Mobil çat səhifələri yalnız son mesajları yüklədiyi üçün siyahının lokal 0…14 indeksi reklam yeri kimi istifadə edilmir. `ChatDto.advertisingSequence` tək çat qrupundakı ümumi sıra nömrəsidir; `advertisingCombinedSequence` 0 və 1 qruplarının birləşmiş Zakaz Qrupu / Suşi axınındakı sıra nömrəsidir. Limitli tarixçə sorğusu bir səhifə və bir əvvəlki mesaj sayı sorğusu ilə qlobal sıra nömrələrini verir. Canlı yaradılma, tək mesaj və redaktə cavabları da sıra nömrələrini qaytarır.

Hər iki tətbiqin əsas/Suşi axınında birləşmiş, Moto Taksi çatında tək qrup sırası istifadə olunur. Reklam 5, 10, 15… nömrəli mesajdan sonra gəlir. Yeni mesaj əlavə ediləndə 15-lik görünən pəncərənin başlanğıcı dəyişsə də, qalan mesajların reklam yerləri dəyişmir. Köhnə backend sıra nömrəsi qaytarmırsa müvəqqəti indeksdən səhv reklam göstərmək əvəzinə həmin mesajda reklam göstərilmir. Bu düzəliş üçün backend deployu və hər iki tətbiqin rebuild-i mütləqdir; DB-yə sütun əlavə olunmur.
