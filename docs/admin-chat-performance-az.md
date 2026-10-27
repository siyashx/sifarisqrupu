# Admin çatlarının yüklənməsi

Admin tətbiqini və backend-i birlikdə yeniləyin. Flutter tətbiqləri üçün bu dəyişiklikdə yeni build lazım deyil.

- `GET /api/v5/push/admin/chat-feed?group=orders|moto` mövcud admin autentifikasiyasından istifadə edir.
- Son 52 mesaj seçilir. Mesajların böyük `thumbnail` sahəsi və tam istifadəçi obyektləri yüklənmir. Mesajları görən şəxslərin adı və `identifyPhoto[0]` profil şəkli toplu sorğu ilə qaytarılır.
- `chat(group_id,id)` indeksi Hibernate-in mövcud schema update axını ilə əlavə edilir.
- Admin ekranları ilk sorğu bitənədək yüklənmə göstərir; sorğu alınmasa boş çat yazmaq yerinə yenidən yoxlama imkanı göstərilir. Geri qayıdarkən sessiyanın yaddaşındakı mesajlar dərhal görünür, arxa planda yenilənir.
- WebSocket HTTP sorğusunu gözləmir, fokus dəyişdikdə köhnə dinləyicilər təmizlənir. Sorğu zamanı gələn canlı mesajlar saxlanılır, eyni ID-li mesaj yenilənir.
- Köhnə backend/native build zamanı uyğunluq yolu da `groupIds` və `limit=52` göndərir. Yeni server/build toplu profil sorğusundan istifadə edir.

Yoxlama: real JPA/H2 testində iki qrupun ayrılması, 52 mesaj limiti, sıralama, ilk profil şəkli, autentifikasiya və entity yükləmədən iki seçmə sorğusu təsdiqlənib. İstehsal serverində vaxt ölçümü və deploy avtomatik aparılmayıb.
