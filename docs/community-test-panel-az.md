# Zakaz Qrupu sınaq paneli — 2.2.0 (31)

Panel yalnız Zakaz Qrupunun debug build-ində görünür: **Menyu → Sınaq paneli**. Əvvəl tətbiqə adi hesabla daxil olun, paneldə admin ID və şifrəsini yazın. Admin şifrəsi cihaz yaddaşına yazılmır; səhifə bağlananda sessiya atılır. Ələhbərə ayrıca sınaq menyusu əlavə edilməyib.

## İşlətmə

1. Yeni backend-i deploy edin, Zakaz Qrupunu `flutter run` ilə yenidən başladın. Normal istifadəçi və admin panel tətbiqlərini ayrıca cihazlarda açıq saxlamaq məcburi deyil.
2. 2, 6 və ya 8 iştirakçı seçib yeni cütləşmə sınağı yaradın. Yalnız hazırkı real hesab iştirak edir; digərləri verilənlər bazasında həqiqi hesab yaratmayan virtual iştirakçılardır.
3. Mərhələni başladın. Normal animasiyanı gözləyə və ya “Adları indi aç” seçə bilərsiniz. “İstifadəçi ekranı” adi canlı səhifəni açır; geri qayıdanda panel yenilənir.
4. Öz hesabınızın seçimini adi səhifədən, digər iştirakçıların seçimlərini paneldən verə bilərsiniz. Hər oyun üçün qalibi seçib qarşılıqlı seçim və hesab təsdiqini avtomatik işlətmək də mümkündür. “İki nəfər də Uddum seçsin” zidd seçimlərin sıfırlanmasını sınayır.
5. Bütün hesablar təsdiqlənəndə növbəti mərhələni başladın. 8 iştirakçı ilə yarımfinal, final və 3-cü yer oyunu yoxlanır. Xal cədvəli və podium adi tətbiq ekranında görünür.
6. Lotoreya üçün yeni sınaq yaradın. Bir bilet real hesaba, iki bilet virtual iştirakçılara aiddir. Yeni elan və canlı dəvət göndərə, öz hesabınızın qalib/uduzan olacağı çarxı başladıb nəticəni görə bilərsiniz. İkinci nəticə ssenarisi üçün yeni lotoreya yaradın. Bildirişin yeni/live/won/lost səsi adi bildiriş sistemi ilə işləyir.
7. Sınaq bitdikdə paneldə “Bu sınağı sil” seçin. Adi yarışlara bu paneldən əməliyyat tətbiq edilmir. Köhnə sınaqlar adminə və hazırkı hesaba görə seçilərək yenidən açıla bilər.

## Təcrid və uyğunluq

Yeni `test_mode`, `test_user_id` sahələri sınaq qeydlərini ayırır. Lotoreyada `test_request_id` təkrar yaratma sorğusunu ayırır. Hazırkı `ddl-auto=update` bu sütunları əlavə edir. Köhnə yarışlarda test_mode false qalır. Sınaq məlumatları yalnız seçilmiş istifadəçiyə və adminə görünür. Yeni elan sorğusu “hamıya” olsa belə sınaqda yalnız həmin hesab hədəflənir; eyni hesabın Zakaz/Ələhbər cihazları ala bilər.

Serverdə `/api/v5/push/admin/community-test/**` bütün əməliyyatlarda admini və sınağın sahibini yoxlayır. Nəticə simulyasiyası adi iştirakçının nəticə metodunu (`admin=false`) işlədir; iki tərəfin təsdiqini bypass etmir. Test olmayan qeyddə əməliyyat rədd olunur. İstifadəçi profilinin ad/telefonu üçün `/api/v5/community/me` yalnız cari hesabın minimal məlumatını verir; parol verilmir.

Paneldə bildiriş müşahidəsi yalnız panel açıq ikən Firebase SDK-nın tətbiqə çatdırdığı mesajı göstərir. Bu, Firebase-ə göndərişi cihazda alınması ilə səhv salmır. Ələhbər və Zakazın cütləşmə/lotoreya Dart kodu və backend qaydaları ortaqdır; ayrıca səs, push, OS icazələri və arxa plan davranışının real iOS/Android cihaz sınağı yenə platformaya aiddir. Tək cihaz bütün digər cihazların şəbəkəsini və OS davranışını təqlid etmir.

## Digər dəyişikliklər

Hər iki tətbiqdə “(Siz)” çıxarılıb. Alqı-satqıda ağ “Pulsuz Elan Yerləşdir” düyməsi var. Bilet üçün WhatsApp mətni qiymət, cari hesabın adı və telefonunu ehtiva edir. Flutter, Android və iOS tətbiq versiyası 2.2.0, build 31-dir.

Bu sessiyada istehsal serverinə deploy, Git push və mağaza üçün release build edilməyib.
