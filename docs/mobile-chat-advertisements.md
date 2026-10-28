# Mobil çat reklamı sorğuları

Hər iki Flutter tətbiqi eyni çat reklamı yaratma və “Reklamlarım” səhifəsini istifadə edir. Formada başlıq, açıqlama, şəkil, WhatsApp/HTTPS hədəfi, müddət və qiymət paketləri, həmçinin canlı önizləmə var. Sorğu uğurla qəbul edildikdə istifadəçinin sorğusu dərhal öz siyahısına əlavə olunur; səhifə bağlanmır. Uğursuz göndərişdə qaralama və idempotency açarı saxlanır. Uğurlu göndərişdən sonra yeni qaralama üçün yeni açar yaradılır.

`GET /api/v5/advertising/mine?channel=MOTO|SUSHI` mövcud Basic autentifikasiyasını tələb edir. İstifadəçi ID-si autentifikasiyadan gəlir, URL-dən qəbul edilmir. Cavab yalnız həmin hesabın həmin çat kanalına aid son 50 reklam sorğusunu ehtiva edir. `items` və `serverNow` qaytarılır; hər sorğuda mövcud reklam məlumatlarından əlavə `displayStatus` var. Aktivlik müddəti keçmiş APPROVED sorğusu üçün `displayStatus=EXPIRED` olur, bazadakı tarixçə dəyişmir. PENDING, APPROVED, REJECTED və CANCELLED sorğuları da sahibinə görünür.

Digər istifadəçilər üçün public feed qaydası saxlanır: yalnız adminin təsdiqlədiyi və yayım vaxtı daxilində olan reklamlar görünür. Öz sorğularını görmək üçün admin panelə müraciət və ya hesabdan çıxıb yenidən daxil olmaq lazım deyil. Eyni hesabla Zakaz Qrupu və Ələhbərdən göndərilmiş eyni kanal reklamları birlikdə görünür.

Backend bu endpointlə deploy edilməlidir. Mobil tətbiqlərin `advertising.dart` və yeni `chat_ad_composer.dart` faylı birlikdə götürülməlidir. Banner əl animasiyası yalnız ekranda və tətbiq aktiv olanda işləyir, cihazda animasiyaları azalt seçimi aktivdirsə statik qalır. Bu işdə production deploy/release build edilməyib.
