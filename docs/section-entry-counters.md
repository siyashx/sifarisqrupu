# Bölmələrə gündəlik giriş sayı

`POST /api/v5/advertising/visit?section=SHOP|SUSHI|MOTO` autentifikasiya olunmuş hər səhifə girişində sayğacı bir vahid artırır. Eyni istifadəçinin təkrar girişi də sayılır. `section` göndərməyən köhnə alqı satqı müştəriləri `SHOP` sayılır.

Zakaz Qrupu ana səhifəsi və Ələhbər Suşi sifarişləri `SUSHI`, hər iki tətbiqin Moto Taksi çat bölməsi `MOTO`, hər iki alqı satqı bölməsi `SHOP` sayılır. Sayğac hesab, tətbiq və platforma üzrə ayrılmır. Gün sərhədi Bakı saatıdır. Hesab dəyişməsi tələb olunmur.

Sayğaclar yeni `community_section_daily_visit` cədvəlində gün/bölmə üzrə saxlanır; atomik SQL artımı paralel girişlərin itirilməsinin qarşısını alır. Köhnə unikal istifadəçi cədvəli saxlanır, amma yeni göstəricidə istifadə olunmur. Yeni metrik deploydan sonra yığılmağa başlayır; əvvəlki unikal istifadəçi saylarından təkrar girişləri bərpa etmək mümkün deyil.

Feed cavabında `visitDay`, `sectionVisits: {SHOP, SUSHI, MOTO}` və köhnə müştəri uyğunluğu üçün `visitors` (= SHOP giriş sayı) var. Tətbiq giriş sorğusunun cavabını dərhal göstərir, digər cihazların sayını mövcud feed yenilənməsi ilə alır. Sadə widget rebuild-i və avtomatik feed yenilənməsi yeni giriş sayılmır; səhifəyə qayıdış yeni giriş sayılır. Şəbəkə xətası zamanı giriş sorğusu avtomatik təkrarlanmır ki, qeyri-müəyyən cavab sayğacı iki dəfə artırmasın.

Backend əvvəl deploy olunmalıdır. Mövcud `ddl-auto=update` ayarında yeni cədvəl açılışda yaranır. Mobil dəyişikliklərdə backend kodları eynidir; hər iki tətbiqdə font asseti əlavə edildiyi üçün tətbiqi yenidən başladın/build edin. Bu işdə deploy və release build edilməyib.

# Çempionata gec qoşulma — mövcud davranış

Bu işdə mərhələ məntiqi dəyişməyib. İlk mərhələ başlamamış admin iştirakçı siyahısını təsdiqləyə bilər; say qrup ölçüsünə tam bölünməlidir. İştirakçı seçimi təsdiqlənən kimi ilk mərhələ avtomatik başlayır. `participants` əməliyyatı ilk mərhələdən sonra yeni istifadəçi əlavə etməyə icazə vermir. Hər oyunun hesabları hər iki tərəfdən təsdiqlənir (və ya admin təsdiqləyir); bütün oyunlar hazır olanda növbəti mərhələ avtomatik başlayır, finaldan sonra yarış tamamlanır.

Təklif: ayrıca qeydiyyat/gözləmə mərhələsi, admin qeydiyyatı bağlayanda ilk mərhələnin başlanması, gec gələnlərin növbəti çempionat üçün gözləmə siyahısına alınması. Davam edən mərhələyə yeni iştirakçı əlavə etmək hazır cədvəli və əvvəlki nəticələrin ədalətli müqayisəsini pozur. Bu təklif hələ tətbiq edilməyib.
