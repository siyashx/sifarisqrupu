# Admin istifadəçiləri və kuryer müraciətləri

Admin panel artıq bütün istifadəçiləri `/api/v5/user` ünvanından endirmir.
Yeni, admin autentifikasiyalı endpoint: `GET /api/v5/push/admin/users`.

Parametrlər:
- `q`: ad və ya telefon üzrə axtarış (maksimum 100 simvol).
- `type`: `all`, `courier`, `customer`. Köhnə ekranla uyğunluq üçün boş rol da kuryer siyahısında qalır.
- `status`: `all` və ya `active` (gözləyən kuryer müraciətləri).
- `sort`: `none` (yeni hesablar əvvəl), `most` və ya `least` (abunəlik prioriteti).
- `page`: 0-dan başlayır; `size`: standart 40, maksimum 100.

Cavab: `items`, `page`, `hasMore`, `total`, `counts.courier`, `counts.customer`.
Saylar yalnız yüklənmiş səhifəni deyil, bütün axtarış nəticələrini əhatə edir.
Axtarış və sıralama səhifələmədən əvvəl bazada aparılır. Bərabər prioritetdə yeni ID əvvəl gəlir.
Telefon/şəkil/abunəlik sahələrindən başqa parol, bildiriş tarixçəsi və cihaz identifikatorları bu endpointdən qaytarılmır.

## Aktivləşdirmə

Əvvəl bu backend versiyasını adi təhlükəsiz deploy qaydası ilə yerləşdirin.
Yeni cədvəl və ya əl ilə SQL migrasiyası tələb olunmur.
Sonra admin tətbiqini yenidən build edin: Android native sorğu icazələrinə `/users` GET yolu əlavə olunub, yalnız Metro yenilənməsi kifayət etmir.
Əvvəlki admin/tətbiq versiyaları üçün `/api/v5/user` saxlanılıb.

## Yoxlama

Backend: `./mvnw -Dtest=AdminUserServiceTest,AdminUserControllerTest,AdminUserDirectoryTest test`
Admin: `node --test test/users-pagination.test.cjs test/courier-screen.test.cjs test/dashboard-counts.test.cjs test/screen-bindings.test.cjs`
Native yol qaydaları: `test/AdminLotteryRoutesCheck.kt`.

2026-11-06: VPS-də yalnız SELECT ilə yeni ID/sıralama sorğuları yoxlanıb: təxminən 15-22 ms (MariaDB komandasının başladılması daxil). Bu ölçü bütöv HTTP sorğusunun və telefon ekranının açılma müddəti deyil.
