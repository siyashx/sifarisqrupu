FROM eclipse-temurin:17-jdk

# JAR dosyasını ekle
ADD target/sifarisqrupu-0.0.1-SNAPSHOT.jar sifarisqrupu.jar

# HTTPS portu aç
EXPOSE 9898

# Uygulamayı başlat
ENTRYPOINT ["java", "-jar", "sifarisqrupu.jar"]
