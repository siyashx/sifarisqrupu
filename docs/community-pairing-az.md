# Cütləşmə, yeni menyu və tarixsiz lotoreya

## Dəyişikliklər

Admin paneldə “Digər bölmələr → Cütləşmə” əlavə edildi. Zakaz Qrupu və Ələhbər ana səhifələrində Menyu düyməsi Cütləşmə, Lotoreya, Alqı-satqı və Ustalar bölmələrini açır. Bölmələrin qırmızı göstəriciləri Menyu düyməsində toplanır. Hər tətbiqin öz menyu rəngi saxlanır.

Lotoreya yaratma, kart və məlumat səhifələrindən keçirilmə tarixi çıxarıldı. Filtrlər Hamısı, Aktiv, Bitmiş oldu. Yeni açıq lotoreyalar yuxarıda, bitmişlər aşağıdadır. Uzun açıqlamada “Davamını oxu” var. Köhnə `draw_at` sütunu məlumatları dağıtmamaq və əvvəlki tətbiqlərlə uyğunluq üçün saxlanır; yeni sistem onu tələb etmir və istifadə etmir.

## Yarış qaydaları

- İlk seçimdə iştirakçı sayı qrup ölçüsünə tam bölünür. Qrup ölçüsü 2–8, ümumi iştirakçı sayı ən çox 256-dır.
- Hər qrupdan bir qalib növbəti mərhələyə keçir. Qalibə 3 xal, uduzana 0 xal verilir. Qrupda 3 və daha çox iştirakçı olduqda bir qalib və qalan uduzanlar olur.
- Sonrakı mərhələdə tək iştirakçı qalarsa, sistem təsadüfi seçilmiş həmin iştirakçını oyun keçirmədən növbəti mərhələyə keçirir. Buna əlavə xal verilmir.
- Sistem növbəti iştirakçıları, yarımfinalı, finalı və iki nəfərlik qruplarda mümkün olduqda ayrıca 3-cü yer oyununu hesablayır. Hər mərhələni admin başladır. Yarımfinalda yalnız bir məğlub varsa, 3-cü yer ona verilir. Yalnız iki iştirakçılı yarışda 3-cü yer olmur.
- Finalda qalib 1-ci, digər finalçı 2-ci olur. Üç və daha çox nəfərlik finalda qalan yerlər final hesabına görə müəyyənləşir. Xal cədvəlində hər qələbə 3 xal qalır; yarış bitdikdə ilk sıralar yekun yerləri göstərir.
- İstifadəçi Uddum/Uduzdum seçir. Qrupda iki qalib və ya heç bir qalib seçilərsə seçimlər sıfırlanır. Hesab 0–999 arasında tam rəqəmlərdən ibarətdir və bərabərliklə bitmir. Hesabı bütün qrup iştirakçıları eyni formada təsdiqləməlidir.
- Admin hesabları düzəldə və sıfırlaya bilər. Köhnə mərhələnin nəticəsi dəyişərsə ona bağlı sonrakı mərhələlər silinir və yenidən başladılır. Canlı animasiya zamanı əvvəlki mərhələyə düzəliş bağlıdır.
- Müddət bütün mərhələyə aiddir; qruplar arasında bərabər bölünür. Cəmi 5–3600 saniyə və hər qrup üçün ən az 3 saniyə tələb olunur.

## Canlı izləmə və bildirişlər

Server qrupları təsadüfi qarışdırır, başlanğıc və açıqlanma vaxtlarını verir. Gələcək qrupların yekun üzvləri açıqlanma vaxtından əvvəl API-dən göstərilmir. Müştərilər server saatına görə eyni mərhələni göstərir; şəbəkəsi kəsilmiş cihazın eyni anda yenilənməsinə zəmanət verilmir. Adların sürətli dəyişməsi yalnız kart hissəsini yeniləyir. Ekranın arxa plana keçməsi səsi dayandırır.

Yaratdıqdan və detaldan təkrar bildiriş göndərərkən Hamıya / İştirakçılara / Heç biri seçilir. Mərhələ başladıqda həmin mərhələnin iştirakçılarına, o cümlədən 3-cü yer oyunu iştirakçılarına bildiriş hazırlanır. Bu bildirişlər sifariş bildirişlərindən ayrıdır; müştəri rolu, sifariş səssizi və offline seçimi cütləşmə bildirişlərini bloklamır. Firebase tokeni və OS bildiriş icazəsi yenə tələb olunur. Android üçün `fcm_pairing_v1` kanalı, iOS üçün `pairing_notify.caf` istifadə edilir. Yaratma və təkrar bildiriş sorğularında requestId təkrar əməliyyatın qarşısını alır.

Lotoreya və cütləşmə canlı səhifələrində göz ikonu izləyənlərin sayını və siyahısını açır. Eyni hesabın bir neçə tətbiq sessiyası bir izləyici sayılır. Heartbeat 10 saniyədir; çıxış və arxa plana keçid siyahıdan çıxarır, əlaqəsi qırılan sessiya 30 saniyədən sonra silinir. Presence cari tək backend instansiyasının yaddaşındadır; backend restartında yenidən toplanır. Bir neçə backend instansiyasına keçilərsə ortaq presence saxlancları tələb olunur.

İzləyici və cütləşmə adlarının solunda profil şəkli, şəkil yoxdursa adın baş hərfi göstərilir. Köhnə `identifyPhoto` şəxsiyyət təsdiqi üçündür və ictimai avatar kimi paylaşılmır. Ayrı, istəyə bağlı `user.profile_image_url` HTTPS ünvanı oxunur; bu dəyişiklik profil şəkli yükləmə forması əlavə etmir.

## API və məlumat bazası

- İstifadəçi: `/api/v5/pairings`, `/{id}`, `/{id}/rounds/{round}/games/{game}/result`.
- Admin: `/api/v5/push/admin/pairings`; users, create, edit, start, notify, status, delete, result və reset əməliyyatları. Üzvlük və admin hüquqları serverdə yoxlanır.
- İzləyicilər: `/api/v5/live/{lottery|pairing}/{id}/viewers`, admin üçün `/api/v5/push/admin/live/...`.
- STOMP: `/topic/pairings`; məlumatlar DB commit-dən sonra yenilənir. Polling ehtiyatı mövcuddur.
- Yeni `community_pairing` cədvəli və `user.profile_image_url` sütunu hazırkı `spring.jpa.hibernate.ddl-auto=update` ayarı ilə backend açılarkən əlavə olunur. Mövcud lotoreya və istifadəçi qeydləri silinmir.

## Yayım

1. Əvvəl yeni backend-i hazırkı rollback-li deploy qaydası ilə yerləşdirin. Normal DB backup siyasətiniz qüvvədə qalsın.
2. Admin paneli yenidən native build edin: Android-də təhlükəsiz API marşrut siyahısı genişləndirilib. Tək Metro/hot reload kifayət deyil.
3. Hər iki Flutter tətbiqini Android və iOS üçün yenidən build edin. Yeni Android raw səsi və iOS Runner resource səsi var. Tək hot reload native bildiriş səsini əlavə etmir.
4. Versiyalar bu tapşırıqda dəyişdirilməyib: hər iki Flutter tətbiqi `2.1.5+30` olaraq qalır.

Bu işdə serverə deploy, Git commit/push və mağaza üçün release build edilməyib.

## Yoxlama

136 backend testi keçdi: tam 8 nəfərlik yarış, 6 nəfərlik keçid, 3 nəfərlik qruplar, H2-də paralel seçimlər və kilidləmə, qarşılıqlı təsdiq, köhnə nəticənin düzəlişi, səlahiyyət və bildiriş uyğunluğu daxil olmaqla. Admin panelin 32 avtomatik testi və native marşrut siyahısının 34 yoxlaması keçdi. Zakaz Qrupu 17, Ələhbər 16 bölmə testi keçdi; dəyişən Flutter bölmələrində analiz təmizdir. Hər iki tətbiqin Android Kotlin kompilyasiyası keçdi. iOS layihə/resource strukturu yoxlanılıb; bu yeni funksiyanın real iOS cihazında səs, push və animasiya sınağı aparılmayıb. Admin paneldə node_modules mövcud olmadığına görə tam native build əvəzinə JS ekran testləri və ayrıca Kotlin marşrut yoxlaması aparılıb.

İlk cihaz yoxlaması üçün: 8 iştirakçı ilə 2 nəfərlik yarış yaradın, bildiriş auditoriyasını seçin, hər iki tətbiqdən canlı açın, ilk mərhələnin hesablarını qarşılıqlı təsdiqləyin, yarımfinalı və final/3-cü yer mərhələsini başladın. Adların, səsin, izləyici sayının, yekun yerlərin və kart filtrlərinin uyğunluğunu yoxlayın.
