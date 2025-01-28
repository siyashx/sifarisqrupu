FROM openjdk:17-jdk
EXPOSE 9898
ADD target/sifarisqrupu-0.0.1-SNAPSHOT.jar sifarisqrupu.jar
HEALTHCHECK --interval=30s --timeout=10s --retries=3 \
  CMD curl --fail http://localhost:9898/ || exit 1
ENTRYPOINT ["java", "-jar", "sifarisqrupu.jar"]
