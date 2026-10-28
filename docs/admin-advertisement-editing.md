# Admin reklam redaktəsi və silinməsi

Admin paneldə çat və elan reklamlarının həm kartında, həm də ətraflı məlumatlarında “Düzəlt” və “Sil” var. Redaktə: başlıq, açıqlama, ImgBB-yə yüklənən şəkil, müddət (gün), qiymət; çat reklamında əlavə olaraq kanal, WhatsApp/link təyinatı. Elan reklamının əsas `listingId` və istifadəçisi dəyişmir, orijinal alqı satqı elanı dəyişdirilmir.

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
