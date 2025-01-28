FROM openjdk:17-jdk
EXPOSE 9292
ADD target/sifarisqrupu-0.0.1-SNAPSHOT.jar sifarisqrupu.jar
HEALTHCHECK --interval=30s --timeout=10s --retries=3 \
  CMD curl --fail http://localhost:9292/ || exit 1
ENTRYPOINT ["java", "-jar", "sifarisqrupu.jar"]
