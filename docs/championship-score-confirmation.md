# Seçimdən dərhal sonra hesabın təsdiqi

İştirakçı “Uddum/Uduzdum” seçdikdən sonra açılan hesab pəncərəsində digərlərinin seçimlərini gözləmədən hesab göndərə bilər. Backend ən azı həmin istifadəçinin öz seçimini tələb edir və hesabın hazırda məlum seçimlərə uyğunluğunu yoxlayır. Hər istifadəçinin təsdiqi eyni hesab üçün saxlanır; bütün iştirakçılar təsdiqləməmiş oyun və növbəti mərhələ tamamlanmır.

Hesab dəyişərsə əvvəlki təsdiqlər silinir. Seçimlər ziddiyyətlidirsə əvvəlki qayda ilə seçimlər, hesablar və təsdiqlər sıfırlanır və revision artır. İstifadəçi öz seçimini saxlanmış hesaba zidd dəyişərsə hesab və təsdiqlər də silinir. Adminin hesabı təsdiqləmə səlahiyyəti olduğu kimi qalır.

Mobil kart və detallarda hesab düyməsi artıq bütün istifadəçilərin seçiminə görə deyil, həmin istifadəçinin öz seçiminə görə açılır. Dəyişiklik hər iki tətbiqdədir. Backend deployu tələb olunur; köhnə backend həmin hesab sorğusuna əvvəlki xətanı qaytaracaq. Bu işdə deploy edilməyib.
